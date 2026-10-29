package willow.train.hexie.vehicle.assembly;

import net.minecraft.server.level.ServerLevel;

/** Discovery is separate from block capture, removal, persistence and creation of moving vehicles. */
@FunctionalInterface
public interface AssemblyScanner {
    AssemblySelection scan(ServerLevel level, AssemblyRequest request) throws ScanException;

    final class ScanException extends Exception {
        public ScanException(String message) { super(message); }
        public ScanException(String message, Throwable cause) { super(message, cause); }
    }
}
