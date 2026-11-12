public class Fallthrough {
    public int value(int input) {
        switch (input) {
            case 1:
                input++;
            case 2:
                return input;
            default:
                return 0;
        }
    }
}
