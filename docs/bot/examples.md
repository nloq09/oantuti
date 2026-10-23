# Bot mẫu có thể chạy được

Các ví dụ bên dưới chép từ bot mẫu đang có trong repository, dùng thư viện chuẩn và chọn action đầu tiên. Tất cả đọc nhiều request JSON Lines trong cùng process, trả đúng sequence và flush từng phản hồi. Chúng giúp bắt đầu protocol, không phải chiến thuật mạnh.

Đọc [Bot Guide](bot-guide.md), [Protocol](protocol.md) và [Limits](limits.md) trước khi chỉnh chiến thuật. Không ghi log ra stdout. Nếu cần debug, dùng stderr và giữ dưới giới hạn runner.

## Dữ liệu thử chung

Từ thư mục gốc repository, dùng [turn.jsonl](examples/turn.jsonl). Kết quả phải khớp [response.jsonl](examples/response.jsonl). Đây là trạng thái còn hai quân, không phải vị trí bắt đầu ván. Giữ cùng một process để thử nhiều dòng liên tiếp.

## Python 3.12

File: [main.py](../../client/assets/bot-examples/main.py).

```python
"""JSON Lines bot. Replace choose_action with your strategy; stdout is protocol only."""
import json
import sys


def choose_action(turn):
    return turn["legalActions"][0]


for line in sys.stdin:
    turn = json.loads(line)
    response = {"type": "ACTION", "sequence": turn["sequence"], "action": choose_action(turn)}
    print(json.dumps(response, separators=(",", ":")), flush=True)
```

Chạy trong Bash/CMD từ gốc repo:

```sh
python client/assets/bot-examples/main.py < docs/bot/examples/turn.jsonl
```

Trong PowerShell:

```powershell
Get-Content -LiteralPath docs/bot/examples/turn.jsonl | python client/assets/bot-examples/main.py
```

## C++17

File: [main.cpp](../../client/assets/bot-examples/main.cpp).

```cpp
// Minimal standard-library starter: echoes the first legal action.
// For a richer strategy, parse state.board.pieces and score all legalActions.
#include <iostream>
#include <string>
#include <stdexcept>

std::string first_action(const std::string& line) {
    auto key = line.find("\"legalActions\"");
    auto start = line.find('{', key);
    if (key == std::string::npos || start == std::string::npos) throw std::runtime_error("Missing actions");
    int depth = 0;
    bool quoted = false, escaped = false;
    for (auto i = start; i < line.size(); ++i) {
        char c = line[i];
        if (quoted) {
            if (escaped) escaped = false;
            else if (c == '\\') escaped = true;
            else if (c == '"') quoted = false;
        } else if (c == '"') quoted = true;
        else if (c == '{') ++depth;
        else if (c == '}' && --depth == 0) return line.substr(start, i - start + 1);
    }
    throw std::runtime_error("Incomplete action");
}

int main() {
    std::string line;
    while (std::getline(std::cin, line)) {
        auto key = line.find("\"sequence\"");
        auto start = line.find(':', key) + 1;
        auto end = line.find(',', start);
        auto sequence = std::stoll(line.substr(start, end - start));
        std::cout << "{\"type\":\"ACTION\",\"sequence\":" << sequence
                  << ",\"action\":" << first_action(line) << "}" << std::endl;
    }
}
```

Mẫu trích nguyên object action bằng bộ quét dấu ngoặc có xử lý chuỗi/escape, rồi trả lại JSON đó; không cần cài thư viện ngoài. Nó chỉ dùng những phần cần thiết của request do server hiện tại sinh. Khi cần đọc đầy đủ board để viết chiến thuật, cần bổ sung parser thay vì tách JSON theo dấu phẩy.

Lưu đoạn C++ thành `main.cpp`, biên dịch/chạy trong Bash:

```sh
g++ -std=c++17 -O2 main.cpp -o bot
./bot < docs/bot/examples/turn.jsonl
```

Nếu đang ở gốc repo trên Windows và đã biên dịch thành `bot.exe`, PowerShell dùng:

```powershell
Get-Content -LiteralPath docs/bot/examples/turn.jsonl | .\bot.exe
```

## Java 21

File: [Main.java](../../client/assets/bot-examples/Main.java).

```java
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
```

Lưu thành `Main.java`, không thêm package. Mẫu Java cũng giữ nguyên JSON của action, xử lý dấu ngoặc/escape và giữ nguyên chữ số sequence; không dùng float để mang sequence.

```sh
javac --release 21 Main.java
java Main < docs/bot/examples/turn.jsonl
```

Dòng chuyển hướng trên dùng Bash/CMD. PowerShell:

```powershell
Get-Content -LiteralPath docs/bot/examples/turn.jsonl | java Main
```

Đóng gói JAR chạy được:

```sh
jar --create --file bot.jar --main-class Main Main.class
java -jar bot.jar < docs/bot/examples/turn.jsonl
```

Chỉ bản đã biên dịch mới là JAR; đổi đuôi `.java` thành `.jar` không tạo chương trình chạy được.

## Kiểm tra tất cả mẫu

Từ repository, với Python, g++ và JDK 21+ đã cài và `JAVA_HOME` đúng:

```sh
python tools/verify-bot-examples.py
```

Script kiểm tra mẫu tin cậy của repo bằng interpreter/compiler local, gồm nhiều lượt liên tiếp, ID có ký tự escape, PASS và bot Python chiến thuật ở P1/P2. Đây không phải kiểm thử Docker hay bằng chứng bot của bạn đã đủ READY trong giải. Không dùng cách chạy host này cho source không tin cậy của người khác.

Có [Python chiến thuật](../../client/assets/bot-examples/tactical.py) để tham khảo chấm điểm giao chiến/nguy hiểm. Sau khi sửa, làm tiếp [Build → Test → Submit](build-test-submit.md).

## Nguồn đối chiếu

- [Bot samples](../../client/assets/bot-examples/), [mẫu workspace](../../server/src/main/resources/code-templates/).
- [BotLanguage](../../server/src/main/java/com/rpsbattle/bot/upload/BotLanguage.java), [script kiểm chứng](../../tools/verify-bot-examples.py).
