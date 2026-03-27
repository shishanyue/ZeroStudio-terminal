package com.rustywarfare.modstudio.shared.net.uri;

import android.net.Uri;

/**
 * The {@link Uri} schemes.
 * <p> <p>
 * <a href="<a">href="https://www.iana.org/assignments/uri-schemes/uri-sche</a>mes.xhtml">...</a>
 * <a href="<a">href="https://en.wikipedia.org/wiki/List_of_UR</a>I_schemes">...</a>
 */
public class UriScheme {

    /** Android app resource. */
    public static final String SCHEME_ANDROID_RESOURCE = "android.resource";

    /** Android content provider. <a href="<a">href="https://www.iana.org/assignments/uri-schemes/pro</a>v/content">...</a>. */
    public static final String SCHEME_CONTENT = "content";

    /** Filesystem or android app asset. <a href="<a">href="https://www.rfc-editor.org/rfc/rfc</a>8089.html">...</a>. */
    public static final String SCHEME_FILE = "file";

    /* Hypertext Transfer Protocol. */
    public static final String SCHEME_HTTP = "http";

    /* Hypertext Transfer Protocol Secure. */
    public static final String SCHEME_HTTPS = "https";

}
