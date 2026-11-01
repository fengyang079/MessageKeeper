import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestRegex {
    public static void main(String[] args) {
        String[] titles = {
            "帮倒忙(5条新消息)",
            "帮倒忙 (5条新消息)",
            "帮倒忙（5条新消息）",
            "帮倒忙(5条未读)",
            "帮倒忙(5条)"
        };
        String regex = "[\\(\\[（]\\s*\\d+\\s*(?:条(?:新消息|消息|未读)?|messages?)\\s*[\\)\\]）]";
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        
        for (String t : titles) {
            Matcher m = p.matcher(t);
            System.out.println("Original: " + t + " -> " + m.replaceAll("").trim());
        }
    }
}
