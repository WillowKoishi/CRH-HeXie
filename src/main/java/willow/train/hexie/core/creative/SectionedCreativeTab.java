package willow.train.hexie.core.creative;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.ItemLike;

/**
 * A {@link CreativeModeTab} supporting scrollable full-row {@link BannerSection} headers via a
 * virtual 9-column grid layout. Real items remain cleanly in vanilla {@code displayItems} and
 * {@code displayItemsSearchTab} without registering any placeholder items.
 */
public class SectionedCreativeTab extends CreativeModeTab {
    public static final int COLUMNS = 9;

    private final List<BiConsumer<ItemDisplayParameters, SectionOutput>> topPopulators;
    private final List<SectionDefinition> sectionDefinitions;
    @Nullable
    private final BannerSection extraItemsTargetBanner;

    private List<ItemStack> rawTopItems = List.of();
    private List<RecordedSection> rawSections = List.of();

    private List<ItemStack> layoutItems = List.of();
    private Int2ObjectMap<BannerSection> bannerRows = new Int2ObjectOpenHashMap<>();
    private Int2ObjectMap<List<ItemStack>> bannerSectionItems = new Int2ObjectOpenHashMap<>();
    @Nullable
    private Collection<ItemStack> layoutDisplaySnapshot;
    private int layoutDisplaySize = -1;

    protected SectionedCreativeTab(
            CreativeModeTab.Builder builder,
            List<BiConsumer<ItemDisplayParameters, SectionOutput>> topPopulators,
            List<SectionDefinition> sectionDefinitions,
            @Nullable BannerSection extraItemsTargetBanner
    ) {
        super(builder);
        this.topPopulators = List.copyOf(topPopulators);
        this.sectionDefinitions = List.copyOf(sectionDefinitions);
        this.extraItemsTargetBanner = extraItemsTargetBanner;
    }

    public static Builder sectionedBuilder() {
        return new Builder();
    }

    @Override
    public void buildContents(ItemDisplayParameters parameters) {
        super.buildContents(parameters);
        rebuildLayout();
    }

    private void generateContents(ItemDisplayParameters parameters, Output vanillaOutput) {
        List<ItemStack> capturedTop = new ArrayList<>();
        List<RecordedSection> capturedSections = new ArrayList<>();
        Set<ItemStack> emittedParent = ItemStackLinkedSet.createTypeAndComponentsSet();
        Set<ItemStack> emittedSearch = ItemStackLinkedSet.createTypeAndComponentsSet();
        FeatureFlagSet enabledFeatures = parameters.enabledFeatures();

        SectionOutput topOutput = new RecordingOutput(vanillaOutput, capturedTop, emittedParent, emittedSearch, enabledFeatures);
        for (BiConsumer<ItemDisplayParameters, SectionOutput> populator : this.topPopulators) {
            populator.accept(parameters, topOutput);
        }

        for (SectionDefinition definition : this.sectionDefinitions) {
            List<ItemStack> capturedSectionItems = new ArrayList<>();
            SectionOutput sectionOutput = new RecordingOutput(
                    vanillaOutput, capturedSectionItems, emittedParent, emittedSearch, enabledFeatures);
            definition.populator().accept(parameters, sectionOutput);
            capturedSections.add(new RecordedSection(definition.banner(), List.copyOf(capturedSectionItems)));
        }

        this.rawTopItems = List.copyOf(capturedTop);
        this.rawSections = List.copyOf(capturedSections);
    }

    /**
     * Rebuilds the virtual 9-column grid layout and banner row index from the current authoritative
     * {@link #getDisplayItems()} (which includes any items added or removed by
     * {@code BuildCreativeModeTabContentsEvent}).
     */
    public void rebuildLayout() {
        Collection<ItemStack> currentDisplayItems = this.getDisplayItems();
        Set<ItemStack> allowed = ItemStackLinkedSet.createTypeAndComponentsSet();
        allowed.addAll(currentDisplayItems);

        Set<ItemStack> declared = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (ItemStack stack : this.rawTopItems) {
            if (!stack.isEmpty() && allowed.contains(stack)) {
                declared.add(stack);
            }
        }
        for (RecordedSection section : this.rawSections) {
            for (ItemStack stack : section.items()) {
                if (!stack.isEmpty() && allowed.contains(stack)) {
                    declared.add(stack);
                }
            }
        }

        List<ItemStack> extraItems = new ArrayList<>();
        for (ItemStack stack : currentDisplayItems) {
            if (!stack.isEmpty() && !declared.contains(stack)) {
                extraItems.add(stack);
            }
        }

        Set<ItemStack> placed = ItemStackLinkedSet.createTypeAndComponentsSet();
        List<ItemStack> items = new ArrayList<>();
        Int2ObjectMap<BannerSection> banners = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<List<ItemStack>> sectionItemsByRow = new Int2ObjectOpenHashMap<>();

        for (ItemStack stack : this.rawTopItems) {
            if (!stack.isEmpty() && allowed.contains(stack) && placed.add(stack)) {
                items.add(stack);
            }
        }

        boolean extrasMergedIntoSection = false;
        for (RecordedSection section : this.rawSections) {
            padToRowBoundary(items);
            int bannerRow = items.size() / COLUMNS;
            banners.put(bannerRow, section.banner());
            for (int i = 0; i < COLUMNS; i++) {
                items.add(ItemStack.EMPTY);
            }
            List<ItemStack> resolvedSectionItems = new ArrayList<>();
            for (ItemStack stack : section.items()) {
                if (!stack.isEmpty() && allowed.contains(stack) && placed.add(stack)) {
                    items.add(stack);
                    resolvedSectionItems.add(stack);
                }
            }
            if (!extrasMergedIntoSection && this.extraItemsTargetBanner != null
                    && this.extraItemsTargetBanner.equals(section.banner())) {
                for (ItemStack stack : extraItems) {
                    if (placed.add(stack)) {
                        items.add(stack);
                        resolvedSectionItems.add(stack);
                    }
                }
                extrasMergedIntoSection = true;
            }
            sectionItemsByRow.put(bannerRow, List.copyOf(resolvedSectionItems));
        }

        if (!extrasMergedIntoSection && !extraItems.isEmpty()) {
            List<ItemStack> remainingExtras = new ArrayList<>();
            for (ItemStack stack : extraItems) {
                if (placed.add(stack)) {
                    remainingExtras.add(stack);
                }
            }
            if (!remainingExtras.isEmpty()) {
                padToRowBoundary(items);
                items.addAll(remainingExtras);
            }
        }

        this.layoutItems = Collections.unmodifiableList(items);
        this.bannerRows = banners;
        this.bannerSectionItems = sectionItemsByRow;
        this.layoutDisplaySnapshot = currentDisplayItems;
        this.layoutDisplaySize = currentDisplayItems.size();
    }

    private void ensureLayoutCurrent() {
        Collection<ItemStack> current = this.getDisplayItems();
        if (this.layoutDisplaySnapshot != current || this.layoutDisplaySize != current.size()) {
            rebuildLayout();
        }
    }

    public List<ItemStack> getLayoutItems() {
        ensureLayoutCurrent();
        return this.layoutItems;
    }

    @Nullable
    public BannerSection getBannerAtRow(int row) {
        ensureLayoutCurrent();
        return this.bannerRows.get(row);
    }

    public List<ItemStack> getSectionItemsAtRow(int row) {
        ensureLayoutCurrent();
        return this.bannerSectionItems.getOrDefault(row, List.of());
    }

    public boolean isBannerRow(int row) {
        ensureLayoutCurrent();
        return this.bannerRows.containsKey(row);
    }

    private static void padToRowBoundary(List<ItemStack> items) {
        int remainder = items.size() % COLUMNS;
        if (remainder != 0) {
            int pad = COLUMNS - remainder;
            for (int i = 0; i < pad; i++) {
                items.add(ItemStack.EMPTY);
            }
        }
    }

    private record SectionDefinition(
            BannerSection banner,
            BiConsumer<ItemDisplayParameters, SectionOutput> populator
    ) {}

    private record RecordedSection(
            BannerSection banner,
            List<ItemStack> items
    ) {}

    /**
     * Output sink passed to top-level and section populators. Extends {@link CreativeModeTab.Output}
     * so existing tab helpers can accept it directly.
     */
    public interface SectionOutput extends CreativeModeTab.Output {
        default void acceptAllItems(ItemLike... items) {
            for (ItemLike item : items) {
                if (item != null) {
                    accept(item);
                }
            }
        }
    }

    private static final class RecordingOutput implements SectionOutput {
        private final Output delegate;
        private final List<ItemStack> recorded;
        private final Set<ItemStack> emittedParent;
        private final Set<ItemStack> emittedSearch;
        private final FeatureFlagSet enabledFeatures;

        private RecordingOutput(
                Output delegate,
                List<ItemStack> recorded,
                Set<ItemStack> emittedParent,
                Set<ItemStack> emittedSearch,
                FeatureFlagSet enabledFeatures
        ) {
            this.delegate = delegate;
            this.recorded = recorded;
            this.emittedParent = emittedParent;
            this.emittedSearch = emittedSearch;
            this.enabledFeatures = enabledFeatures;
        }

        @Override
        public void accept(ItemStack stack, TabVisibility visibility) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            boolean wantsParent = visibility != TabVisibility.SEARCH_TAB_ONLY;
            boolean wantsSearch = visibility != TabVisibility.PARENT_TAB_ONLY;

            boolean addParent = wantsParent && this.emittedParent.add(stack);
            boolean addSearch = wantsSearch && this.emittedSearch.add(stack);
            if (!addParent && !addSearch) {
                return;
            }

            TabVisibility effectiveVisibility;
            if (addParent && addSearch) {
                effectiveVisibility = TabVisibility.PARENT_AND_SEARCH_TABS;
            } else if (addParent) {
                effectiveVisibility = TabVisibility.PARENT_TAB_ONLY;
            } else {
                effectiveVisibility = TabVisibility.SEARCH_TAB_ONLY;
            }

            this.delegate.accept(stack, effectiveVisibility);
            if (addParent && stack.getItem().isEnabled(this.enabledFeatures)) {
                this.recorded.add(stack);
            }
        }
    }

    /**
     * Fluent DSL builder wrapping {@link CreativeModeTab#builder()} and
     * {@link CreativeModeTab.Builder#withTabFactory(java.util.function.Function)}.
     */
    public static final class Builder {
        private final CreativeModeTab.Builder delegate = CreativeModeTab.builder();
        private final List<BiConsumer<ItemDisplayParameters, SectionOutput>> topPopulators = new ArrayList<>();
        private final List<SectionDefinition> sections = new ArrayList<>();
        @Nullable
        private BannerSection extraItemsTargetBanner;

        public Builder title(Component title) {
            this.delegate.title(title);
            return this;
        }

        public Builder hideTitle() {
            this.delegate.hideTitle();
            return this;
        }

        public Builder icon(Supplier<ItemStack> iconGenerator) {
            this.delegate.icon(iconGenerator);
            return this;
        }

        public Builder withTabsBefore(ResourceLocation... tabs) {
            this.delegate.withTabsBefore(tabs);
            return this;
        }

        @SafeVarargs
        public final Builder withTabsBefore(ResourceKey<CreativeModeTab>... tabs) {
            this.delegate.withTabsBefore(tabs);
            return this;
        }

        public Builder withTabsAfter(ResourceLocation... tabs) {
            this.delegate.withTabsAfter(tabs);
            return this;
        }

        @SafeVarargs
        public final Builder withTabsAfter(ResourceKey<CreativeModeTab>... tabs) {
            this.delegate.withTabsAfter(tabs);
            return this;
        }

        public Builder backgroundTexture(ResourceLocation texture) {
            this.delegate.backgroundTexture(texture);
            return this;
        }

        public Builder withLabelColor(int labelColor) {
            this.delegate.withLabelColor(labelColor);
            return this;
        }

        public Builder withSlotColor(int slotColor) {
            this.delegate.withSlotColor(slotColor);
            return this;
        }

        public Builder configureTab(Consumer<CreativeModeTab.Builder> customizer) {
            Objects.requireNonNull(customizer, "customizer").accept(this.delegate);
            return this;
        }

        /** Adds unbannered items before the first section banner. */
        public Builder topItems(Consumer<SectionOutput> populator) {
            Objects.requireNonNull(populator, "populator");
            this.topPopulators.add((params, out) -> populator.accept(out));
            return this;
        }

        /** Adds unbannered items before the first section banner with access to display parameters. */
        public Builder topItems(BiConsumer<ItemDisplayParameters, SectionOutput> populator) {
            this.topPopulators.add(Objects.requireNonNull(populator, "populator"));
            return this;
        }

        /** Adds a bannered category section. */
        public Builder section(BannerSection banner, Consumer<SectionOutput> populator) {
            Objects.requireNonNull(banner, "banner");
            Objects.requireNonNull(populator, "populator");
            this.sections.add(new SectionDefinition(banner, (params, out) -> populator.accept(out)));
            return this;
        }

        /** Adds a bannered category section with access to display parameters. */
        public Builder section(BannerSection banner, BiConsumer<ItemDisplayParameters, SectionOutput> populator) {
            Objects.requireNonNull(banner, "banner");
            Objects.requireNonNull(populator, "populator");
            this.sections.add(new SectionDefinition(banner, populator));
            return this;
        }

        /**
         * Routes extra items injected via {@code BuildCreativeModeTabContentsEvent} into the
         * specified section instead of appending them unbannered at the end.
         */
        public Builder appendExtraItemsTo(BannerSection targetBanner) {
            this.extraItemsTargetBanner = Objects.requireNonNull(targetBanner, "targetBanner");
            return this;
        }

        public SectionedCreativeTab build() {
            List<BiConsumer<ItemDisplayParameters, SectionOutput>> topCopy = List.copyOf(this.topPopulators);
            List<SectionDefinition> sectionsCopy = List.copyOf(this.sections);
            BannerSection extraTarget = this.extraItemsTargetBanner;
            AtomicReference<SectionedCreativeTab> selfRef = new AtomicReference<>();

            this.delegate.displayItems((params, output) -> {
                SectionedCreativeTab tab = selfRef.get();
                if (tab != null) {
                    tab.generateContents(params, output);
                }
            });
            this.delegate.withTabFactory(tabBuilder -> {
                SectionedCreativeTab tab = new SectionedCreativeTab(tabBuilder, topCopy, sectionsCopy, extraTarget);
                selfRef.set(tab);
                return tab;
            });

            return (SectionedCreativeTab) this.delegate.build();
        }
    }
}
