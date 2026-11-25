package willow.train.hexie.core.api.display;

@FunctionalInterface
public interface PassengerDisplaySource {
    PassengerDisplayState read();
}
