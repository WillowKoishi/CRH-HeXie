package willow.train.hexie.render.init;

import willow.train.hexie.render.model.CarriageNoSignModel;
import willow.train.hexie.render.model.ElectricFenceDoorRightModel;
import willow.train.hexie.render.model.LacqueredBoardLogo;

public final class ClientModEventSubscriber {

    /**
     * 记得在这里注册自己的模型，像以下这个例子写就好，对应的模型在 EntityModel 包里
     *
     * @param event -
     */
    public static void registerModelLayers() {
        ClientModelLayers.registerModelLayer(
                CarriageNoSignModel.CARRIAGE_NO_SIGN_MODEL_LAYER,
                CarriageNoSignModel::createBodyLayer);
        ClientModelLayers.registerModelLayer(
                LacqueredBoardLogo.LAYER_A, LacqueredBoardLogo::createBodyLayer);
        ClientModelLayers.registerModelLayer(
                ElectricFenceDoorRightModel.RIGHT_LAYER,
                ElectricFenceDoorRightModel::createBodyLayer);
    }
}
