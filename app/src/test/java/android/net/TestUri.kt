package android.net

import android.os.Parcel

class TestUri(private val value: String) : Uri() {
    override fun buildUpon(): Uri.Builder = error("not needed in JVM contract tests")
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = value
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String = value
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = value
    override fun getPath(): String = value
    override fun getPathSegments(): List<String> = listOf(value)
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String = "content"
    override fun getSchemeSpecificPart(): String = value
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun toString(): String = value
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}
