package willow.train.hexie.api.display;

/** Client-side views bind to their carriage's synchronized passenger information. */
public interface PassengerDisplayHost {
    void passengerDisplaySource(PassengerDisplaySource source);
    PassengerDisplayState passengerDisplay();
    String carriageNumber();
    void setCarriageNumber(String number);
}
