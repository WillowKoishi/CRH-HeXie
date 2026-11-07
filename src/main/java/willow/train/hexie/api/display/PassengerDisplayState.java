package willow.train.hexie.api.display;

/** Read-only passenger information; empty fields mean the timetable has not supplied them. */
public record PassengerDisplayState(String trainNumber, String origin, String destination, String nextStop) {
    public static final PassengerDisplayState EMPTY = new PassengerDisplayState("", "", "", "");

    public PassengerDisplayState {
        trainNumber = clean(trainNumber);
        origin = clean(origin);
        destination = clean(destination);
        nextStop = clean(nextStop);
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.codePoints().filter(c -> !Character.isISOControl(c) && c != 0xA7).limit(128)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().strip();
    }
}
