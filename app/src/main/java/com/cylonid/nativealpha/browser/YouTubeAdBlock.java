package com.cylonid.nativealpha.browser;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * High-performance YouTube ad-blocking engine for mobile & desktop WebViews.
 * Uses visual CSS suppression, native skip button clicking, and harmless 16x ad acceleration.
 * Never seeks or corrupts MSE/AV01 video buffer pipelines.
 */
public final class YouTubeAdBlock {
    private YouTubeAdBlock() {}

    public static boolean supports(String url) {
        if (url == null) return false;
        try {
            Uri parsed = Uri.parse(url);
            String host = parsed.getHost();
            if (host == null) return false;
            String lower = host.toLowerCase(Locale.ROOT);
            return lower.equals("youtube.com")
                    || lower.endsWith(".youtube.com")
                    || lower.equals("youtu.be")
                    || lower.endsWith(".youtu.be");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean shouldIntercept(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return false;
            String lowerHost = host.toLowerCase(Locale.ROOT);

            // Block third-party ad servers without touching first-party video playback
            return lowerHost.contains("doubleclick.net")
                    || lowerHost.contains("googleads")
                    || lowerHost.contains("pagead2.googlesyndication.com")
                    || lowerHost.contains("adservice.google.");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static WebResourceResponse createEmptyResponse() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        return new WebResourceResponse("text/plain", "utf-8", 204, "No Content", headers, new ByteArrayInputStream(new byte[0]));
    }

    public static final String SCRIPT = """
        (() => {
          if (window.__nativeAlphaYouTubeAdsInstalled) return;
          window.__nativeAlphaYouTubeAdsInstalled = true;

          // 1. Inject comprehensive CSS to hide banner, promo, feed, and popup ads
          const injectStyle = () => {
            if (document.getElementById('__na_yt_ad_style')) return;
            const style = document.createElement('style');
            style.id = '__na_yt_ad_style';
            style.textContent = `
              ytd-ad-slot-renderer,
              ytd-display-ad-renderer,
              ytd-promoted-sparkles-web-renderer,
              ytm-promoted-sparkles-web-renderer,
              ytm-promoted-sparkles-text-search-web-renderer,
              ytm-companion-ad-renderer,
              ytd-companion-slot-renderer,
              ytm-promoted-video-renderer,
              ytm-ad-slot-renderer,
              ytd-banner-promo-renderer,
              ytd-statement-banner-renderer,
              #player-ads,
              #merch-shelf,
              .ad-showing .ytp-ad-overlay-container,
              .ytp-ad-message-container,
              .ytp-ad-action-interstitial,
              .ytp-ad-image-overlay,
              ytm-pivot-bar-item-renderer[tab-identifier="FEshared"],
              .upsell-dialog-renderer,
              ytm-mealbar-promo-renderer {
                display: none !important;
              }
            `;
            const root = document.head || document.documentElement;
            if (root) root.appendChild(style);
          };

          injectStyle();
          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', injectStyle);
          }

          // 2. Video Player Auto-Skip & 16x Ad Acceleration
          const SKIP_SELECTORS = [
            '.ytp-ad-skip-button',
            '.ytp-ad-skip-button-modern',
            '.ytp-skip-ad-button',
            '.ytp-ad-skip-button-slot',
            '.videoAdUiSkipButton',
            'button.ytp-ad-skip-button-modern',
            '.ytp-ad-overlay-close-button',
            '.ytp-ad-overlay-close-container'
          ];

          const isAdPlaying = () => {
            const player = document.querySelector('#movie_player, .html5-video-player');
            if (player) {
              return player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');
            }
            const adElem = document.querySelector('.ad-showing, .ad-interrupting');
            return !!adElem;
          };

          let wasAdPlaying = false;

          const handleAdPlayback = () => {
            const player = document.querySelector('#movie_player, .html5-video-player');

            // 1. Try native player skipAd method
            if (player && typeof player.skipAd === 'function') {
              try { player.skipAd(); } catch (e) {}
            }

            // 2. Click any available skip button
            for (const selector of SKIP_SELECTORS) {
              const buttons = document.querySelectorAll(selector);
              for (const btn of buttons) {
                if (btn && !btn.disabled && typeof btn.click === 'function') {
                  btn.click();
                }
              }
            }

            // 3. Accelerate ad to 16x speed and mute without modifying currentTime
            const video = document.querySelector('video');
            if (video && isAdPlaying()) {
              if (!video.dataset.adOriginalRate) {
                video.dataset.adOriginalRate = String(video.playbackRate || 1);
                video.dataset.adOriginalMuted = video.muted ? '1' : '0';
              }
              video.muted = true;
              video.playbackRate = 16.0;
            }
          };

          const restorePlaybackIfNeeded = () => {
            const video = document.querySelector('video');
            if (video && video.dataset.adOriginalRate) {
              const origRate = parseFloat(video.dataset.adOriginalRate);
              video.playbackRate = (!isNaN(origRate) && origRate > 0) ? origRate : 1.0;
              if (video.dataset.adOriginalMuted === '0') {
                video.muted = false;
              }
              delete video.dataset.adOriginalRate;
              delete video.dataset.adOriginalMuted;
            }
          };

          const checkState = () => {
            if (isAdPlaying()) {
              wasAdPlaying = true;
              handleAdPlayback();
            } else if (wasAdPlaying) {
              wasAdPlaying = false;
              restorePlaybackIfNeeded();
            }
          };

          // 3. Continuous observers & ticker
          let intervalId = setInterval(checkState, 200);

          let observer = null;
          const observePlayer = () => {
            injectStyle();
            const player = document.querySelector('#movie_player, .html5-video-player');
            if (player && (!observer || observer.__target !== player)) {
              if (observer) observer.disconnect();
              observer = new MutationObserver(() => checkState());
              observer.__target = player;
              observer.observe(player, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['class']
              });
            }
            checkState();
          };

          observePlayer();

          // 4. SPA Navigation events (m.youtube.com & youtube.com)
          const onNav = () => {
            injectStyle();
            setTimeout(observePlayer, 100);
            setTimeout(observePlayer, 500);
          };

          window.addEventListener('yt-navigate-finish', onNav);
          window.addEventListener('yt-page-data-updated', onNav);
          window.addEventListener('popstate', onNav);
          window.addEventListener('hashchange', onNav);

          const origPush = history.pushState;
          history.pushState = function(...args) {
            origPush.apply(this, args);
            onNav();
          };
          const origReplace = history.replaceState;
          history.replaceState = function(...args) {
            origReplace.apply(this, args);
            onNav();
          };

          window.addEventListener('pagehide', () => {
            if (intervalId) clearInterval(intervalId);
            if (observer) observer.disconnect();
            window.__nativeAlphaYouTubeAdsInstalled = false;
          });
          window.addEventListener('pageshow', () => {
            if (!window.__nativeAlphaYouTubeAdsInstalled) {
              window.__nativeAlphaYouTubeAdsInstalled = true;
              intervalId = setInterval(checkState, 200);
              observePlayer();
            }
          });
        })();
        """;
}
