package willow.train.hexie.render.sign;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import static willow.train.hexie.render.sign.SignTextRenderer.normalize;
import static willow.train.hexie.render.sign.SignTextTextures.Entry;
import static willow.train.hexie.render.sign.SignTextTextures.entry;

// split from willow/train/hexie/client/sign/SignTextRenderer.java, lines 223-262
public final class SignTextMetrics {
    /** Actual fitted ink height, for anchoring a number's bottom independently of string length. */
    public static float height(ResourceLocation profile, String text, float height, float maxWidth) {
        text = normalize(text);
        if (text.isBlank()) return 0;
        var loaded = SignFontProfiles.get(profile);
        if (loaded == null || loaded.font().canDisplayUpTo(text) != -1) {
            var font = Minecraft.getInstance().font;
            return Math.min(height, maxWidth * font.lineHeight / Math.max(1, font.width(text)));
        }
        var entry = entry(profile, text, loaded);
        return entry == null ? 0 : Math.min(height, maxWidth * entry.inkHeight / entry.inkWidth);
    }

    /** Ink-space position of the digit nearest the description, measured in the full shaped line. */
    public static float digitCenter(ResourceLocation profile,String text,float height,float maxWidth,boolean first) {
        text=normalize(text);if(text.isBlank())return 0;
        var loaded=SignFontProfiles.get(profile);
        if(loaded!=null&&loaded.font().canDisplayUpTo(text)==-1) {
            var entry=entry(profile,text,loaded);if(entry==null)return 0;
            float scale=Math.min(height/entry.inkHeight,maxWidth/entry.inkWidth);
            return (first?entry.firstDigitCenter:entry.lastDigitCenter)*scale;
        }
        var font=Minecraft.getInstance().font;int index=-1;
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i);if(Character.isDigit(cp)){index=i;if(first)break;}i+=Character.charCount(cp);
        }
        if(index<0)index=first?0:text.offsetByCodePoints(text.length(),-1);
        int end=index+Character.charCount(text.codePointAt(index));
        float scale=Math.min(height/font.lineHeight,maxWidth/Math.max(1,font.width(text)));
        return (font.width(text.substring(0,index))+font.width(text.substring(index,end))/2f)*scale;
    }
    public static float width(ResourceLocation profile, String text, float height, float maxWidth) {
        text = normalize(text);
        if (text.isBlank() || !Float.isFinite(height) || !Float.isFinite(maxWidth) || height <= 0 || maxWidth <= 0) return 0;
        var loaded = SignFontProfiles.get(profile);
        if (loaded == null || loaded.font().canDisplayUpTo(text) != -1)
            return Math.min(maxWidth, Minecraft.getInstance().font.width(text) * height / Minecraft.getInstance().font.lineHeight);
        Entry entry = entry(profile, text, loaded);
        return entry == null ? 0 : Math.min(maxWidth, height * entry.inkWidth / entry.inkHeight);
    }
}
