package br.com.cookiebot.telegram;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SocialMediaEmbedInterceptor {

    // Regex pré-compiladas estaticamente (Proibido compilar no hot-path)
    private static final Pattern TWITTER_PATTERN = Pattern.compile("https?://(?:www\\.)?(?:twitter\\.com|x\\.com)/([a-zA-Z0-9_]+/status/[0-9]+(?:\\?[^\\s]*)?)");
    private static final Pattern TIKTOK_PATTERN = Pattern.compile("https?://(?:www\\.)?tiktok\\.com/(@[a-zA-Z0-9_.]+/video/[0-9]+(?:\\?[^\\s]*)?)");
    private static final Pattern INSTAGRAM_PATTERN = Pattern.compile("https?://(?:www\\.)?instagram\\.com/((?:p|reel)/[a-zA-Z0-9_-]+(?:/[^\\s]*)?)");

    public String rewriteLinks(String originalText) {
        if (originalText == null || originalText.isBlank()) {
            return originalText;
        }

        String result = originalText;
        
        // Uso direto e alocação mínima via Matcher.replaceAll()
        Matcher twitterMatcher = TWITTER_PATTERN.matcher(result);
        if (twitterMatcher.find()) {
            result = twitterMatcher.replaceAll("https://fxtwitter.com/$1");
        }

        Matcher tiktokMatcher = TIKTOK_PATTERN.matcher(result);
        if (tiktokMatcher.find()) {
            result = tiktokMatcher.replaceAll("https://vxtiktok.com/$1");
        }

        Matcher instagramMatcher = INSTAGRAM_PATTERN.matcher(result);
        if (instagramMatcher.find()) {
            result = instagramMatcher.replaceAll("https://ddinstagram.com/$1");
        }

        return result;
    }
}