package willow.train.hexie.init.client;

import willow.train.hexie.client.model.*;

public final class ClientModEventSubscriber {

    /**
     * 记得在这里注册自己的模型，像以下这个例子写就好，对应的模型在 EntityModel 包里
     *
     * @param event -
     */
    public static void registerModelLayers() {
        HeXieClientInit.registerModelLayer(
                CarriageNoSignModel.CARRIAGE_NO_SIGN_MODEL_LAYER,
                CarriageNoSignModel::createBodyLayer);
        HeXieClientInit.registerModelLayer(
                LacqueredBoardLogo.LAYER_A, LacqueredBoardLogo::createBodyLayer);
        HeXieClientInit.registerModelLayer(
                ElectricFenceDoorRightModel.RIGHT_LAYER,
                ElectricFenceDoorRightModel::createBodyLayer);
    }
}
