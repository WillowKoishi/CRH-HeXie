package willow.train.hexie.compat.create.signal;

import willow.train.hexie.core.api.cab.CtcsProfile.Code;

/** Client-visible aspect, separate from Create's interlocking state. */
public interface CreateSignalAspect {
    Code hexie$ctcsCode();
}
