package dev.amsam0.voicechatdiscord;

public class Version {
    private final long[] components = new long[3];

    public Version(long a, long b, long c) {
        components[0] = a;
        components[1] = b;
        components[2] = c;
    }

    public static Version parseChecked(String input) throws NumberFormatException {
        return parseUnchecked(input);
    }

    public static Version parseUnchecked(String input) {
        Version version = new Version(0, 0, 0);

        char[] chars = input.toCharArray();
        int charIndex = 0;

        int component = 0;
        while (charIndex < chars.length && component < version.components.length) {
            StringBuilder rawComponent = new StringBuilder();

            while (charIndex < chars.length) {
                if (Character.isDigit(chars[charIndex])) {
                    rawComponent.append(chars[charIndex]);
                    charIndex++; // Go to next character
                } else {
                    break; // No number found, done with component. Leave character to be checked later
                }
            }

            if (!rawComponent.isEmpty()) {
                version.components[component] = Long.parseLong(rawComponent.toString());
            }
            component++;

            if (charIndex < chars.length && chars[charIndex] == '.') {
                charIndex++; // Go to next character
            } else {
                break; // Neither number nor period found, done with version
            }
        }

        return version;
    }

    @Override
    public String toString() {
        return components[0] + "." + components[1] + "." + components[2];
    }

    public boolean isLowerThan(Version other) {
        if (components[0] != other.components[0]) {
            return components[0] < other.components[0];
        }
        if (components[1] != other.components[1]) {
            return components[1] < other.components[1];
        }
        return components[2] < other.components[2];
    }

    public boolean isHigherThanOrEquivalentTo(Version other) {
        if (components[0] != other.components[0]) {
            return components[0] > other.components[0];
        }
        if (components[1] != other.components[1]) {
            return components[1] > other.components[1];
        }
        return components[2] >= other.components[2];
    }
}
