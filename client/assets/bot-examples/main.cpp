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
