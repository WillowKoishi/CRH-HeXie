package dev.hexie.renderer.neoforge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.Pose;
import dev.hexie.renderer.iris.*;
import dev.hexie.renderer.mc.*;
import dev.hexie.renderer.mc.ViewPassOwnership.Pass;
import net.minecraft.client.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import java.io.IOException;
import java.io.UncheckedIOException;
import static dev.hexie.renderer.mc.ClientRenderState.*;

public final class HexieClient {
    private static HexieClient instance;
    private final PrototypeRenderer backend=new PrototypeRenderer();
    private final boolean iris=ModList.get().isLoaded("iris"), sodium=ModList.get().isLoaded("sodium");
    private net.minecraft.client.multiplayer.ClientLevel level;
    private long frame;

    public static synchronized void install(IEventBus bus) {
        if (instance != null) return;
        try {
            instance = new HexieClient(bus);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize built-in HeXie renderer", e);
        }
    }

    private HexieClient(IEventBus bus) throws IOException {
        config=FMLPaths.CONFIGDIR.get().resolve("hexie-render.json");
        demo=new DemoSceneController(backend);
        engine().settings(RenderSettings.load(config));
        if (iris) {
            IrisFrames.geometry=(view,pass,camera,matrix)->draw(view,pass,camera,matrix,IrisFrames.shadowProjection());
            IrisFrames.expectShadow=(view,transparent)->StandardGeometry.expect(view,true,transparent);
        }
        bus.addListener(this::reload);
        var events=NeoForge.EVENT_BUS;
        events.addListener(this::begin);
        events.addListener(this::stage);
        events.addListener(this::tick);
        events.addListener(this::commands);
    }
    private void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)manager->{
            backend.reload();
            StandardGeometry.dispose();
            if (iris) IrisFrames.invalidate();
        });
    }
    private void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level!=null && !Minecraft.getInstance().isPaused()) demo.tick();
    }
    private void begin(RenderFrameEvent.Pre event) {
        timings.begin(System.nanoTime());
        var mc=Minecraft.getInstance();
        if (frame==0) maxTextureSize=org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_MAX_TEXTURE_SIZE);
        if (level!=mc.level) {
            // Never wipe host sections that ChunkEvent.Load already registered for the newly joined level!
            if (mc.level == null) {
                demo.clear();
            } else {
                demo.clearDemoOnly();
            }
            if (level!=null) backend.reload();
            level=mc.level;
        }
        boolean full=sodium && GL.getCapabilities().OpenGL45 && !Minecraft.ON_OSX;
        var tier=engine().route().begin(++frame,()->iris && IrisFrames.sample(),full);
        reason=switch(tier) {
            case BASIC -> sodium?"OpenGL 4.5 unavailable":"vanilla terrain";
            case IRIS -> "shader pack owns direct light";
            case FULL -> "multi-light full tier";
        };
        terrainBatches=shipBatches=wrapperMismatches=0;
        presentAll(frame,event.getPartialTick().getGameTimeDeltaPartialTick(false));
        var snap = engine().snapshot();
        if (level != null && (snap == null || (snap.sections().isEmpty() && snap.frame().lights().isEmpty()))) {
            backend.prepareWorldIfIdle(level, engine().settings());
        }
    }
    private record View(Object renderer, Camera camera,net.minecraft.world.phys.Vec3 origin,Matrix4f matrix,Matrix4f projection) {}
    private void stage(RenderLevelStageEvent event) {
        if (level==null || engine().snapshot()==null || engine().snapshot().sections().isEmpty() || iris && IrisFrames.inShadow()) return;
        var s=event.getStage();
        var view=new View(event.getLevelRenderer(),event.getCamera(),event.getCamera().getPosition(),
                new Matrix4f(event.getModelViewMatrix()),new Matrix4f(event.getProjectionMatrix()));
        if (s==RenderLevelStageEvent.Stage.AFTER_SKY) { StandardGeometry.expect(view,false,true); return; }
        Pass pass;
        if (s==RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) pass=Pass.SOLID;
        else if (s==RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) pass=Pass.CUTOUT;
        else if (s==RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) pass=Pass.TRANSLUCENT;
        else return;
        draw(view,pass,event.getCamera(),view.matrix(),view.projection());
    }
    private void draw(Object view,Pass pass,Camera camera,Matrix4f matrix,Matrix4f projection) {
        if (engine().snapshot()==null || engine().snapshot().sections().isEmpty()) return;
        Runnable standard=()->StandardGeometry.draw(view,pass,camera,matrix,projection,backend.volume);
        if (engine().route().tier()==RenderSettings.Tier.IRIS) PassTarget.standard(standard);
        else standard.run();
    }
    private void commands(RegisterClientCommandsEvent event) {
        var root=Commands.literal("hexie_render");
        for (String scene:new String[]{"demo","corridor"}) root.then(Commands.literal(scene).executes(c->{
            var player=Minecraft.getInstance().player;
            if (player==null) return 0;
            double yaw=-Math.toRadians(player.getYRot());
            var at=player.position().add(4*Math.sin(yaw),0,4*Math.cos(yaw));
            demo.start(new Pose(new Vec(at.x,at.y,at.z),0,Math.sin(yaw/2),0,Math.cos(yaw/2)),scene.equals("corridor"));
            return reply(c.getSource(),scene);
        }));
        root.then(Commands.literal("lights").then(Commands.argument("n",IntegerArgumentType.integer(0,8192)).executes(c->{
            demo.lights(IntegerArgumentType.getInteger(c,"n"));
            return reply(c.getSource(),"lights="+demo.lights());
        })));
        var preset=Commands.literal("preset");
        for (var p:RenderSettings.Preset.values()) preset.then(Commands.literal(p.name().toLowerCase(java.util.Locale.ROOT)).executes(c->{
            engine().settings(RenderSettings.of(p)); save();
            return reply(c.getSource(),"preset="+p);
        }));
        root.then(preset);
        root.then(Commands.literal("stats").executes(c->reply(c.getSource(),stats())));
        root.then(Commands.literal("clear").executes(c->{ demo.clear(); return reply(c.getSource(),"cleared"); }));
        event.getDispatcher().register(root);
    }
    private static int reply(CommandSourceStack source,String text) {
        source.sendSystemMessage(Component.literal("HeXie | "+text));
        return 1;
    }
}
