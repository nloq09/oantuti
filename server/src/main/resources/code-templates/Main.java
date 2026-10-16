import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Standard-library starter. Keep public class Main and do not declare a package. */
public class Main {
    private static final Pattern SEQUENCE = Pattern.compile("\"sequence\"\\s*:\\s*(\\d+)");

    static String chooseAction(String line) {
        int key = line.indexOf("\"legalActions\"");
        int start = line.indexOf('{', key);
        if (key < 0 || start < 0) throw new IllegalArgumentException("Missing actions");
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = start; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return line.substring(start, i + 1);
        }
        throw new IllegalArgumentException("Incomplete action");
    }

    public static void main(String[] args) throws Exception {
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String line; (line = input.readLine()) != null;) {
            var sequence = SEQUENCE.matcher(line);
            if (!sequence.find()) throw new IllegalArgumentException("Missing sequence");
            System.out.println("{\"type\":\"ACTION\",\"sequence\":" + sequence.group(1)
                    + ",\"action\":" + chooseAction(line) + "}");
            System.out.flush();
        }
    }
}
