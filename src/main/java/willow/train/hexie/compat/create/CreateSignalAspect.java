package willow.train.hexie.compat.create;

import willow.train.hexie.vehicle.control.CtcsControl.Code;

/** Client-visible aspect, separate from Create's interlocking state. */
public interface CreateSignalAspect {
    Code hexie$ctcsCode();
}
