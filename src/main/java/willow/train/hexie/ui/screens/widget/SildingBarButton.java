package willow.train.hexie.ui.screens.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractOptionSliderButton;
import net.minecraft.network.chat.Component;

// split from willow/train/hexie/client/screens/TrainSpeedSignEditScreen.java
public class SildingBarButton extends AbstractOptionSliderButton {

    private final Component msg;

    private boolean onClicked;

    public SildingBarButton(
            Minecraft pMinecraft,
            int pX,
            int pY,
            int pWidth,
            int pHeight,
            double pValue,
            Component msg) {
        super(pMinecraft.options, pX, pY, pWidth, pHeight, pValue);
        this.value = pValue;
        this.msg = msg;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(
                msg.copy()
                        .append(Component.literal(" = " + String.format("%.3f", value - 0.5))));
    }

    @Override
    protected void applyValue() {}

    public void setValue(double value) {
        this.value = value;
        updateMessage();
    }

    @Override
    public void onClick(double pMouseX, double pMouseY) {
        super.onClick(pMouseX, pMouseY);
        this.onClicked = true;
    }

    @Override
    public void onRelease(double pMouseX, double pMouseY) {
        super.onRelease(pMouseX, pMouseY);
        this.onClicked = false;
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if(!onClicked) return false;
        double percentage = (pMouseX - this.getX())/(double) this.width;
        this.setValue(percentage);
        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }

    public double getValue() {
        return value;
    }
}
