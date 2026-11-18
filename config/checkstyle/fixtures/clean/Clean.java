public final class Clean {
    record Point(int x, int y) {}

    public int length(Object value) {
        return switch (value) {
            case String text -> text.length();
            case Point(int x, int y) -> x + y;
            default -> 0;
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Clean;
    }

    @Override
    public int hashCode() {
        return 1;
    }
}
