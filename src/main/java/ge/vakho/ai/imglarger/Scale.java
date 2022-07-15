package ge.vakho.ai.imglarger;

public enum Scale {
    X2("2"),
    X4("4"),
    X8("8");

    private final String value;

    Scale(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
