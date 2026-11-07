package willow.train.hexie.api.display;

@FunctionalInterface
public interface PassengerDisplaySource {
    PassengerDisplayState read();
}
