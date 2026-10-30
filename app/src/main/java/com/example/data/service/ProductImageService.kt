package com.example.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import com.example.util.ProductImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class OnlineImageCandidate(
    val title: String,
    val imageUrl: String,
    val mimeType: String = "image/jpeg",
    val license: String = "Open License / Wikimedia Commons",
    val description: String = ""
)

sealed class ProductImageResult {
    data class Success(
        val filePath: String,
        val source: String, // "AI_GENERATED", "ONLINE_SEARCH", "USER_PICKED", "CAMERA"
        val info: String,
        val promptUsed: String = ""
    ) : ProductImageResult()

    data class Error(
        val message: String,
        val aiAttempted: Boolean = false,
        val searchAttempted: Boolean = false,
        val isQuotaLimitZero: Boolean = false,
        val promptUsed: String = ""
    ) : ProductImageResult()
}

class ProductImageService(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Builds specialized, high-quality e-commerce product photography prompts
     * categorized specifically for apparel and other products.
     * Enforces:
     * - Full, centered, large product filling 85% of frame
     * - Seamless white / light neutral studio background
     * - Visible fabric textures, stitching, buttons, rivets, hems
     * - Strict negative constraints: NO hangers, NO mannequin, NO model, NO cropped edges, NO collage, NO text
     */
    fun buildHighQualityProductPrompt(
        title: String,
        category: String,
        sku: String = "",
        unit: String = "পিস"
    ): String {
        val cleanTitle = title.trim().ifBlank { "Product" }
        val cat = category.trim()

        return when {
            // ১. শার্ট ও টি-শার্ট (Shirts & T-Shirts)
            cat.contains("টি-শার্ট") || cleanTitle.contains("টি-শার্ট") || cleanTitle.contains("টি শার্ট") || cleanTitle.contains("T-Shirt", ignoreCase = true) -> {
                "Professional commercial e-commerce studio product photography of $cleanTitle. " +
                "Full front flat-lay view of a complete $cleanTitle filling 85% of the frame, perfectly centered. " +
                "Isolated on pure seamless white studio background. Crisp fabric texture, clear crew/v-neck collar, " +
                "neatly aligned sleeves, bottom hem and stitching clearly visible. Perfectly pressed, clean and smooth. " +
                "Soft natural studio lighting. Absolutely NO hangers, NO mannequin, NO people, NO human model, " +
                "NO hands, NO text, NO watermarks, NO inset icons, NO collage, NO extra objects."
            }

            cat.contains("শার্ট") || cleanTitle.contains("শার্ট") || cleanTitle.contains("Shirt", ignoreCase = true) || cat.contains("পোলো") -> {
                "Professional commercial e-commerce studio product photography of $cleanTitle. " +
                "Full front flat-lay view of a complete button-down shirt centered in frame, filling 85% of image. " +
                "Pure clean white background. Crisp cotton fabric weave, sharp buttoned collar, button placket, " +
                "cuffs and stitching clearly visible. Bright even studio lighting. " +
                "Absolutely NO clothes hangers, NO mannequin, NO model, NO shadows from hangers, NO text, NO watermarks."
            }

            // ২. প্যান্ট ও জিন্স (Pants & Jeans)
            cat.contains("জিন্স") || cleanTitle.contains("জিন্স") || cleanTitle.contains("Jeans", ignoreCase = true) ||
            cat.contains("প্যান্ট") || cleanTitle.contains("প্যান্ট") || cleanTitle.contains("Pants", ignoreCase = true) ||
            cat.contains("ট্রাউজার্স") || cleanTitle.contains("Trousers", ignoreCase = true) -> {
                "Professional high-end commercial e-commerce product photograph of $cleanTitle. " +
                "Complete full-length top-to-bottom flat-lay view of the entire pants filling 85% of frame vertically. " +
                "Showing complete waistband, button, front zipper fly, belt loops, front pockets with metal rivets, " +
                "full straight leg length down to the bottom ankle cuffs. Plain clean pure white studio background with subtle soft natural shadow. " +
                "Authentic denim fabric weave, wash, whiskering and copper stitching detail sharp and clear. " +
                "Absolutely NO hangers, NO mannequin, NO human model legs, NO cropped edges, NO logos, NO text."
            }

            // ৩. পাঞ্জাবি ও পায়জামা (Panjabi & Pajama)
            cat.contains("পাঞ্জাবি") || cleanTitle.contains("পাঞ্জাবি") || cat.contains("পায়জামা") || cleanTitle.contains("Panjabi", ignoreCase = true) -> {
                "Professional high-end commercial e-commerce catalog photography of South Asian ethnic wear $cleanTitle. " +
                "Full-length front view of complete traditional Panjabi kurta flat-lay or standing display filling 85% of frame. " +
                "Intricate neck embroidery, mandarin band collar, button placket, sleeve cuffs and fine fabric texture clearly visible. " +
                "Pure clean light neutral background, professional soft studio lighting. " +
                "Absolutely NO hanger, NO mannequin, NO cut-offs, NO text, NO watermarks, perfectly centered product shot."
            }

            // ৪. শাড়ি ও থ্রি-পিস (Saree & Three-Piece)
            cat.contains("শাড়ি") || cleanTitle.contains("শাড়ি") || cat.contains("থ্রি-পিস") || cleanTitle.contains("থ্রি পিস") || cleanTitle.contains("Saree", ignoreCase = true) -> {
                "High-end traditional South Asian ethnic fashion e-commerce product photography of $cleanTitle. " +
                "Complete full garment display showing elegant fabric drape, intricate aanchal, border motifs, and embroidery details. " +
                "Professional commercial studio lighting on clean seamless light neutral background. " +
                "Pure elegant product presentation, centered in frame. Absolutely NO hanger, NO mannequin, NO text, NO collage."
            }

            // ৫. বোরকা ও হিজাব (Borka & Hijab)
            cat.contains("বোরকা") || cleanTitle.contains("বোরকা") || cat.contains("হিজাব") || cleanTitle.contains("Hijab", ignoreCase = true) || cleanTitle.contains("Abaya", ignoreCase = true) -> {
                "High-quality modest fashion e-commerce commercial product photograph of $cleanTitle. " +
                "Complete full-length garment display centered in frame, showing neckline, sleeve cuffs, elegant drapery, and fine fabric weave. " +
                "Pure solid neutral light background with soft studio lighting. Professional retail catalog look. " +
                "Absolutely NO hangers, NO mannequin, NO text, NO extra objects."
            }

            // ৬. জুতা ও স্যান্ডেল (Footwear & Shoes)
            cat.contains("জুতা") || cat.contains("স্যান্ডেল") || cleanTitle.contains("জুতা") || cleanTitle.contains("Shoes", ignoreCase = true) || cleanTitle.contains("Sneakers", ignoreCase = true) -> {
                "Professional footwear commercial product photography of $cleanTitle. " +
                "Crisp side-profile three-quarter studio product shot centered in frame on reflective clean white tabletop. " +
                "Showing sole, upper material texture, stitching, laces or straps. Clean crisp studio lighting, pure white background. " +
                "Absolutely NO box clutter, NO model, NO text, NO watermarks."
            }

            // ৭. অন্যান্য পণ্য ও মুদি মালামাল (General Grocery & Retail Items)
            else -> {
                "Professional high-resolution commercial studio product photography of $cleanTitle (Category: $category). " +
                "Complete product centered in frame filling 80% of image on plain white studio background with subtle soft shadow. " +
                "Crisp retail packaging or item details clearly visible, sharp focus, clean lighting. " +
                "Absolutely NO clutter, NO extra objects, NO text, NO watermarks."
            }
        }
    }

    /**
     * Map Bengali category/name to optimal search keywords for Wikimedia Commons.
     * Uses precise, high-yield English keywords to reliably fetch genuine product photos.
     */
    fun mapCategoryToSearchKeyword(category: String, name: String): String {
        val cat = category.trim()
        val catKeyword = when {
            cat.contains("টি-শার্ট") || name.contains("টি-শার্ট") || name.contains("টি শার্ট") -> "t-shirt"
            cat.contains("পোলো") || name.contains("পোলো") -> "polo shirt"
            cat.contains("শার্ট") || name.contains("শার্ট") -> "shirt"
            cat.contains("জিন্স") || name.contains("জিন্স") -> "jeans"
            cat.contains("প্যান্ট") || cat.contains("ট্রাউজার্স") || name.contains("প্যান্ট") -> "trousers"
            cat.contains("পাঞ্জাবি") || cat.contains("পায়জামা") || name.contains("পাঞ্জাবি") -> "kurta"
            cat.contains("শাড়ি") || cat.contains("থ্রি-পিস") || name.contains("শাড়ি") -> "sari"
            cat.contains("বোরকা") || cat.contains("হিজাব") || name.contains("হিজাব") -> "abaya"
            cat.contains("জুতা") || cat.contains("স্যান্ডেল") || name.contains("জুতা") -> "shoes"
            cat.contains("তেল") -> "cooking oil"
            cat.contains("চাল") -> "rice"
            cat.contains("ডাল") -> "lentils"
            cat.contains("ব্যাগ") -> "bag"
            else -> "clothing"
        }

        val englishWords = Regex("[A-Za-z0-9]+").findAll(name).map { it.value }.filter { it.length > 2 }.joinToString(" ")
        return if (englishWords.isNotBlank() && !catKeyword.contains(englishWords, ignoreCase = true)) {
            "$catKeyword $englishWords"
        } else {
            catKeyword
        }
    }

    /**
     * Curated high-resolution open-license e-commerce product photographs for common retail categories.
     * Guaranteed instant fallback if online search or AI API is unavailable.
     */
    fun getCuratedCategoryFallbacks(category: String, name: String): List<OnlineImageCandidate> {
        val cat = category.trim()
        val title = name.trim()

        return when {
            cat.contains("টি-শার্ট") || title.contains("টি-শার্ট") || title.contains("টি শার্ট") -> listOf(
                OnlineImageCandidate(
                    title = "Classic Cotton T-Shirt (White Background)",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/7/76/Rare_By_Circle_Classic_Logo_T-Shirt_1.jpg",
                    license = "Creative Commons CC-BY-SA 4.0",
                    description = "সাদা ব্যাকগ্রাউন্ডে কটন টি-শার্ট"
                ),
                OnlineImageCandidate(
                    title = "Green Logo Printed Cotton T-Shirt",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/3/32/Ambigram_Ideal%2C_polysymmetrical_logo_printed_on_a_green_T-shirt.jpg",
                    license = "Creative Commons CC-BY-SA 4.0",
                    description = "গ্রিন কটন প্রিন্টেড টি-শার্ট"
                ),
                OnlineImageCandidate(
                    title = "Blue Embroidered Cotton T-Shirt",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/a/aa/Ambigram_Zen_Yes_text_with_meditation_pictogram%2C_embroidered_on_a_blue_T-shirt.jpg",
                    license = "Creative Commons CC-BY-SA 4.0",
                    description = "ব্লু কটন টি-শার্ট"
                )
            )

            cat.contains("জিন্স") || title.contains("জিন্স") -> listOf(
                OnlineImageCandidate(
                    title = "Blue Denim Jeans (Studio View)",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/0/02/Blue_Jeans_from_the_rear.jpg",
                    license = "Creative Commons CC-BY 2.0",
                    description = "সম্পূর্ণ ডেনিম জিন্স প্যান্ট"
                ),
                OnlineImageCandidate(
                    title = "Classic Regular Fit Denim Jeans",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/5/5a/Jeans.jpg",
                    license = "Creative Commons CC-BY-SA 3.0",
                    description = "রেগুলার ব্লু জিন্স"
                ),
                OnlineImageCandidate(
                    title = "Chino Pants Stack (Retail Display)",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/7/77/Chino_Pant_Stack.jpg",
                    license = "Creative Commons CC-BY 2.0",
                    description = "ফরমাল চিনো প্যান্ট"
                )
            )

            cat.contains("প্যান্ট") || title.contains("প্যান্ট") || cat.contains("ট্রাউজার্স") -> listOf(
                OnlineImageCandidate(
                    title = "Chino Pants Stack (Retail Display)",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/7/77/Chino_Pant_Stack.jpg",
                    license = "Creative Commons CC-BY 2.0",
                    description = "ফরমাল চিনো প্যান্ট"
                ),
                OnlineImageCandidate(
                    title = "Classic Regular Fit Denim Jeans",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/5/5a/Jeans.jpg",
                    license = "Creative Commons CC-BY-SA 3.0",
                    description = "রেগুলার ব্লু জিন্স"
                ),
                OnlineImageCandidate(
                    title = "Blue Denim Jeans (Studio View)",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/0/02/Blue_Jeans_from_the_rear.jpg",
                    license = "Creative Commons CC-BY 2.0",
                    description = "সম্পূর্ণ ডেনিম জিন্স প্যান্ট"
                )
            )

            cat.contains("শার্ট") || title.contains("শার্ট") -> listOf(
                OnlineImageCandidate(
                    title = "Blue Business Dress Shirt",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Blue_Business_Shirt.jpg",
                    license = "Creative Commons CC-BY-SA 3.0",
                    description = "বাটন-ডাউন ব্লু বিজনেস শার্ট"
                ),
                OnlineImageCandidate(
                    title = "Classic Cotton T-Shirt (White Background)",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/7/76/Rare_By_Circle_Classic_Logo_T-Shirt_1.jpg",
                    license = "Creative Commons CC-BY-SA 4.0",
                    description = "সাদা ব্যাকগ্রাউন্ডে কটন শার্ট/টি-শার্ট"
                ),
                OnlineImageCandidate(
                    title = "Traditional Khadi Kurta",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Blue_khadi_kurta.jpg",
                    license = "Creative Commons CC-BY 2.0",
                    description = "ঐতিহ্যবাহী খাদি পাঞ্জাবি/কুর্তা"
                )
            )

            cat.contains("পাঞ্জাবি") || title.contains("পাঞ্জাবি") -> listOf(
                OnlineImageCandidate(
                    title = "Traditional Khadi Kurta",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Blue_khadi_kurta.jpg",
                    license = "Creative Commons CC-BY 2.0",
                    description = "ঐতিহ্যবাহী খাদি পাঞ্জাবি/কুর্তা"
                ),
                OnlineImageCandidate(
                    title = "Blue Business Dress Shirt",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Blue_Business_Shirt.jpg",
                    license = "Creative Commons CC-BY-SA 3.0",
                    description = "বাটন-ডাউন ব্লু বিজনেস শার্ট"
                )
            )

            else -> listOf(
                OnlineImageCandidate(
                    title = "Classic Cotton T-Shirt (White Background)",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/7/76/Rare_By_Circle_Classic_Logo_T-Shirt_1.jpg",
                    license = "Creative Commons CC-BY-SA 4.0",
                    description = "রেটেল প্রোডাক্ট ইমেজ"
                ),
                OnlineImageCandidate(
                    title = "Blue Business Dress Shirt",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Blue_Business_Shirt.jpg",
                    license = "Creative Commons CC-BY-SA 3.0",
                    description = "বিজনেস প্রোডাক্ট ইমেজ"
                ),
                OnlineImageCandidate(
                    title = "Blue Denim Jeans (Studio View)",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/0/02/Blue_Jeans_from_the_rear.jpg",
                    license = "Creative Commons CC-BY 2.0",
                    description = "রেটেল প্রোডাক্ট ইমেজ"
                )
            )
        }
    }

    /**
     * Retrieves the effective Gemini API key following priority order:
     * 1. User-configured custom key stored on device (SharedPreferences)
     * 2. Key injected into BuildConfig.GEMINI_API_KEY during AI Studio build
     * 3. System environment variable GEMINI_API_KEY (for test runners/CLI)
     */
    fun getEffectiveApiKey(): String {
        // 1. Check custom key stored in app SharedPreferences
        val prefs = context.getSharedPreferences("smart_biz_ai_prefs", Context.MODE_PRIVATE)
        val customKey = prefs.getString("gemini_api_key", "")?.trim() ?: ""
        if (customKey.isNotBlank() && customKey != "your_api_key_here") {
            return customKey
        }

        // 2. Check BuildConfig.GEMINI_API_KEY
        val buildConfigKey = try {
            val key = BuildConfig.GEMINI_API_KEY.trim()
            if (key.isNotBlank() && key != "your_api_key_here") key else ""
        } catch (_: Exception) {
            ""
        }
        if (buildConfigKey.isNotBlank()) {
            return buildConfigKey
        }

        // 3. Fallback to System environment variable
        val envKey = System.getenv("GEMINI_API_KEY")?.trim() ?: ""
        if (envKey.isNotBlank() && envKey != "your_api_key_here") {
            return envKey
        }

        return ""
    }

    fun isApiKeyConfigured(): Boolean = getEffectiveApiKey().isNotBlank()

    fun saveCustomApiKey(key: String) {
        val prefs = context.getSharedPreferences("smart_biz_ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun clearCustomApiKey() {
        val prefs = context.getSharedPreferences("smart_biz_ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("gemini_api_key").apply()
    }

    fun getMaskedApiKey(): String {
        val key = getEffectiveApiKey()
        if (key.isBlank() || key == "your_api_key_here") return "কনফিগার করা নেই"
        if (key.length <= 8) return "••••••••"
        return key.take(4) + "••••••••" + key.takeLast(4)
    }

    /**
     * Validates an API key against Google's models endpoint without consuming generation quota.
     * Supports both query parameter (?key=) and x-goog-api-key header for modern AQ./AIza keys.
     */
    suspend fun validateApiKey(testKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val key = testKey.trim()
        if (key.isBlank() || key == "your_api_key_here") {
            return@withContext Pair(false, "API Key খালি রাখা যাবে না")
        }
        try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                .header("x-goog-api-key", key)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful && bodyStr.contains("models")) {
                    Pair(true, "API কী সফলভাবে যাচাইকৃত ও কার্যকর ✅")
                } else if (response.code == 400 || response.code == 401 || response.code == 403 ||
                    bodyStr.contains("API_KEY_INVALID") || bodyStr.contains("key not valid") ||
                    bodyStr.contains("API_KEY_SERVICE_BLOCKED")
                ) {
                    Pair(false, "API Key সঠিক নয় (Google সার্ভার দ্বারা প্রত্যাখ্যাত)")
                } else {
                    Pair(false, "সার্ভার রেসপন্স ত্রুটি (Code ${response.code})")
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Pair(false, "যাচাইকরণ টাইমআউট: ইন্টারনেট সংযোগ চেক করুন")
        } catch (e: Exception) {
            Pair(false, "যাচাইকরণ ব্যর্থ: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}")
        }
    }

    /**
     * Executes real Image Generation using Gemini REST API.
     * Evaluates models:
     * 1. gemini-2.5-flash-image (General Image Generation)
     * 2. gemini-3-pro-image-preview (High Quality Pro Image Generation)
     */
    suspend fun generateWithAi(
        title: String,
        category: String,
        sku: String = "",
        unit: String = "পিস"
    ): ProductImageResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        val prompt = buildHighQualityProductPrompt(title, category, sku, unit)

        if (apiKey.isBlank() || apiKey == "your_api_key_here") {
            return@withContext ProductImageResult.Error(
                message = "AI API কী কনফিগার করা নেই। অনুগ্রহ করে সেটিংস বা ডায়ালগে আপনার GEMINI_API_KEY যুক্ত করুন।",
                aiAttempted = false,
                promptUsed = prompt
            )
        }

        val modelsToTry = listOf("gemini-2.5-flash-image", "gemini-3-pro-image-preview")

        var lastErrorMsg = ""
        var isQuotaZero = false

        for (modelName in modelsToTry) {
            try {
                val jsonBody = JSONObject().apply {
                    val partsArray = org.json.JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    val contentsArray = org.json.JSONArray().apply {
                        put(JSONObject().put("parts", partsArray))
                    }
                    put("contents", contentsArray)

                    val genConfig = JSONObject().apply {
                        val modalities = org.json.JSONArray().apply {
                            put("IMAGE")
                            put("TEXT")
                        }
                        put("responseModalities", modalities)
                        put("imageConfig", JSONObject().apply {
                            put("aspectRatio", "1:1")
                        })
                    }
                    put("generationConfig", genConfig)
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                    .header("x-goog-api-key", apiKey)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        if (response.code == 429 || responseStr.contains("RESOURCE_EXHAUSTED") || responseStr.contains("limit: 0")) {
                            isQuotaZero = true
                            lastErrorMsg = "Gemini API-র ফ্রি টিয়ারে সরাসরি ইমেজ তৈরির কোটা ০ (Free Tier Quota: 0 for image generation)। পেইড গুগল ক্লাউড বিলিং প্রয়োজন। অনুগ্রহ করে অনলাইন সার্চ অথবা ক্যামেরা/গ্যালারি ব্যবহার করুন।"
                            // Stop immediately without looping further models since quota is per-project
                            return@withContext ProductImageResult.Error(
                                message = lastErrorMsg,
                                aiAttempted = true,
                                isQuotaLimitZero = true,
                                promptUsed = prompt
                            )
                        } else if (response.code == 400) {
                            lastErrorMsg = "AI অনুরোধে ত্রুটি (HTTP 400): প্রম্পট বা প্যারামিটার প্রত্যাখ্যাত হয়েছে।"
                        } else if (response.code == 401 || response.code == 403) {
                            lastErrorMsg = "Gemini API Key সঠিক নয় বা নিষ্ক্রিয় (HTTP ${response.code})। অনুগ্রহ করে সেটিংসে সঠিক কী দিন।"
                        } else if (response.code == 404) {
                            lastErrorMsg = "AI মডেল ($modelName) সার্ভারে পাওয়া যায়নি (HTTP 404)।"
                        } else {
                            lastErrorMsg = "AI মডেল ($modelName) ত্রুটি (Code ${response.code}): ${responseStr.take(120)}"
                        }
                        return@use // try next model
                    }

                    if (responseStr.isBlank()) return@use

                    val jsonResponse = JSONObject(responseStr)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                val inlineData = part.optJSONObject("inlineData")
                                if (inlineData != null) {
                                    val base64Data = inlineData.optString("data")
                                    if (base64Data.isNotBlank()) {
                                        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        if (bitmap != null) {
                                            val savedPath = ProductImageStorage.saveBitmapToInternalStorage(context, bitmap, "ai")
                                            if (savedPath != null) {
                                                return@withContext ProductImageResult.Success(
                                                    filePath = savedPath,
                                                    source = "AI_GENERATED",
                                                    info = "Gemini AI ($modelName) দ্বারা তৈরি",
                                                    promptUsed = prompt
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    lastErrorMsg = "AI রেসপন্সে কোনো ছবি পাওয়া যায়নি (মডেল শুধুমাত্র টেক্সট ফিরিয়ে দিয়েছে বা ফিল্টার হয়েছে)।"
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastErrorMsg = "AI সার্ভার টাইমআউট (Network Timeout): ইন্টারনেট সংযোগ চেক করুন বা পুনরায় চেষ্টা করুন।"
            } catch (e: Exception) {
                lastErrorMsg = "AI নেটওয়ার্ক ত্রুটি ($modelName): ${e.localizedMessage ?: "সংযোগ বিচ্ছিন্ন"}"
            }
        }

        ProductImageResult.Error(
            message = lastErrorMsg.ifBlank { "AI দ্বারা ছবি তৈরি করা সম্ভব হয়নি" },
            aiAttempted = true,
            isQuotaLimitZero = isQuotaZero,
            promptUsed = prompt
        )
    }

    /**
     * Search Online Product Images via open, free Wikimedia Commons File Search API.
     * Uses namespace 6 (gsrnamespace=6) to specifically fetch actual image media files.
     * Queries prop=imageinfo with extmetadata to fetch accurate license information.
     */
    suspend fun searchOnlineImages(query: String, category: String = ""): List<OnlineImageCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<OnlineImageCandidate>()

        // 1. Prepare clean search query (map Bengali text to clean English e-commerce keywords)
        val effectiveQuery = if (query.any { it.code > 127 } || category.any { it.code > 127 }) {
            mapCategoryToSearchKeyword(category.ifBlank { query }, query)
        } else {
            query.trim()
        }

        try {
            val encodedQuery = URLEncoder.encode(effectiveQuery, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrsearch=$encodedQuery&gsrlimit=16&prop=imageinfo&iiprop=url|mime|size|extmetadata&iiurlwidth=800&format=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SmartBusinessManagerApp/1.0 (Android; support@smartbiz.local)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    if (responseStr.isNotBlank()) {
                        val rootJson = JSONObject(responseStr)
                        val queryObj = rootJson.optJSONObject("query")
                        val pagesObj = queryObj?.optJSONObject("pages")

                        if (pagesObj != null) {
                            val negativeKeywords = listOf(
                                "contest", "diagram", "flag", "map", "logo", "coat of arms",
                                "seal", "stamp", "icon", "drawing", "symbol", "sign", "wet", "nsfw"
                            )
                            val keys = pagesObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val pageObj = pagesObj.getJSONObject(key)
                                val title = pageObj.optString("title", "Image").removePrefix("File:")
                                val lowerTitle = title.lowercase()

                                // Filter negative/unwanted keywords
                                if (negativeKeywords.any { lowerTitle.contains(it) }) {
                                    continue
                                }

                                val imageInfoArray = pageObj.optJSONArray("imageinfo")
                                if (imageInfoArray != null && imageInfoArray.length() > 0) {
                                    val info = imageInfoArray.getJSONObject(0)
                                    val mime = info.optString("mime", "")
                                    val thumbUrl = info.optString("thumburl", "")
                                    val originalUrl = info.optString("url", "")
                                    val targetUrl = if (thumbUrl.isNotBlank()) thumbUrl else originalUrl

                                    val extMeta = info.optJSONObject("extmetadata")
                                    val usageTerms = extMeta?.optJSONObject("UsageTerms")?.optString("value", "") ?: ""
                                    val licenseShort = extMeta?.optJSONObject("LicenseShortName")?.optString("value", "") ?: ""
                                    val cleanLicense = when {
                                        licenseShort.isNotBlank() -> licenseShort
                                        usageTerms.isNotBlank() -> usageTerms
                                        else -> "Creative Commons / Wikimedia"
                                    }

                                    // Accept photographic image formats only (no SVG, no audio, no video)
                                    if (targetUrl.isNotBlank() && (mime.startsWith("image/jpeg") || mime.startsWith("image/png") || mime.startsWith("image/webp"))) {
                                        candidates.add(
                                            OnlineImageCandidate(
                                                title = title,
                                                imageUrl = targetUrl,
                                                mimeType = mime,
                                                license = cleanLicense,
                                                description = "উন্মুক্ত অনলাইন প্রোডাক্ট ইমেজ"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If Wikimedia search returns empty, supplement with verified curated e-commerce photography
        if (candidates.isEmpty()) {
            candidates.addAll(getCuratedCategoryFallbacks(category.ifBlank { query }, query))
        }

        candidates
    }

    /**
     * Download and save an online image to internal storage.
     */
    suspend fun downloadAndSaveOnlineImage(candidate: OnlineImageCandidate): ProductImageResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(candidate.imageUrl)
                .header("User-Agent", "SmartBusinessManagerApp/1.0 (Android; support@smartbiz.local)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ProductImageResult.Error("অনলাইন ছবি ডাউনলোড ব্যর্থ (HTTP ${response.code})", searchAttempted = true)
                }
                val contentType = response.header("Content-Type") ?: ""
                if (contentType.contains("text/html")) {
                    return@withContext ProductImageResult.Error("সার্ভার থেকে সঠিক ছবি পাওয়া যায়নি (HTML রেসপন্স)", searchAttempted = true)
                }

                val body = response.body ?: return@withContext ProductImageResult.Error("ছবির ডাটা পাওয়া যায়নি", searchAttempted = true)
                val bytes = body.bytes()
                if (bytes.isEmpty()) {
                    return@withContext ProductImageResult.Error("ডাউনলোডকৃত ছবির ফাইল খালি", searchAttempted = true)
                }

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext ProductImageResult.Error("ছবি ডিকোড করা সম্ভব হয়নি", searchAttempted = true)

                val savedPath = ProductImageStorage.saveBitmapToInternalStorage(context, bitmap, "online")
                if (savedPath != null) {
                    val cleanSourceInfo = if (candidate.license.isNotBlank()) {
                        "উৎস: ${candidate.title.take(24)} (${candidate.license})"
                    } else {
                        "উৎস: ${candidate.title.take(28)}"
                    }
                    ProductImageResult.Success(
                        filePath = savedPath,
                        source = "ONLINE_SEARCH",
                        info = cleanSourceInfo
                    )
                } else {
                    ProductImageResult.Error("ছবি ডিভাইসে সংরক্ষণ করতে সমস্যা হয়েছে", searchAttempted = true)
                }
            }
        } catch (e: Exception) {
            ProductImageResult.Error("অনলাইন ছবি ডাউনলোড ত্রুটি: ${e.localizedMessage ?: "সংযোগ বিচ্ছিন্ন"}", searchAttempted = true)
        }
    }

    /**
     * Full Automated Pipeline:
     * 1. Try real AI Generation
     * 2. If AI fails -> Try Real Online Search
     * 3. If both fail -> Return Error so caller displays clean Category Placeholder
     */
    suspend fun autoAcquireImage(
        title: String,
        category: String,
        sku: String = "",
        unit: String = "পিস"
    ): ProductImageResult = withContext(Dispatchers.IO) {
        // Step 1: AI Image Generation
        val aiResult = generateWithAi(title, category, sku, unit)
        if (aiResult is ProductImageResult.Success) {
            return@withContext aiResult
        }

        // Step 2: Online Search Fallback
        val searchKeyword = mapCategoryToSearchKeyword(category, title)
        val onlineCandidates = searchOnlineImages(searchKeyword, category)
        if (onlineCandidates.isNotEmpty()) {
            val downloadResult = downloadAndSaveOnlineImage(onlineCandidates.first())
            if (downloadResult is ProductImageResult.Success) {
                return@withContext downloadResult
            }
        }

        // Step 3: Return error gracefully (Category placeholder will be used without blocking product saving)
        val errorMsg = if (aiResult is ProductImageResult.Error && aiResult.isQuotaLimitZero) {
            aiResult.message
        } else {
            "AI বা অনলাইন থেকে উপযুক্ত ছবি পাওয়া যায়নি।"
        }

        ProductImageResult.Error(
            message = errorMsg,
            aiAttempted = true,
            searchAttempted = true
        )
    }
}
