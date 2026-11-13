package com.example.multiplicationquiz

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.net.URL
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import android.graphics.drawable.GradientDrawable
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.util.Locale
import kotlin.random.Random

class MainActivity : Activity() {
    private val green = Color.rgb(23, 107, 85)
    private val dark = Color.rgb(27, 42, 38)
    private val pale = Color.rgb(239, 247, 243)
    private val PICK_IMAGE = 701

    private lateinit var root: LinearLayout
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private var uid = ""
    private var playerName = ""
    private var avatarUrl = ""
    private var roomCode = ""
    private var roomRef: DatabaseReference? = null
    private var roomListener: ValueEventListener? = null
    private var isHost = false
    private var localAnswered = false
    private var currentQ = 0
    private var currentStatus = "waiting"
    private var profileNameInput: EditText? = null
    private var profileStatus: TextView? = null
    private var pendingAvatarUri: Uri? = null
    private var savedResultForRoom = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.initializeApp(this)
            auth = FirebaseAuth.getInstance()
            db = FirebaseDatabase.getInstance()
            storage = FirebaseStorage.getInstance()
            val user = auth.currentUser
            if (user != null) {
                uid = user.uid
                loadProfile()
            } else {
                auth.signInAnonymously().addOnSuccessListener {
                    uid = it.user?.uid ?: ""
                    loadProfile()
                }.addOnFailureListener { showError("تعذر الاتصال: ${it.localizedMessage}") }
            }
        } catch (e: Exception) {
            showError("إعداد Firebase غير مكتمل. راجع تعليمات الإعداد في README_AR.md")
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float, color: Int = dark, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_RTL
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    private fun btn(label: String) = Button(this).apply {
        text = label; textSize = 16f; isAllCaps = false; setTextColor(Color.WHITE)
        background = rounded(green, 14); setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    private fun input(hintText: String, numeric: Boolean = false) = EditText(this).apply {
        hint = hintText; textSize = 17f; gravity = Gravity.CENTER
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
        setSingleLine(true); setPadding(dp(12), dp(8), dp(12), dp(8))
        background = rounded(Color.WHITE, 12)
    }
    private fun params(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun setupScreen(title: String, subtitle: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(26), dp(22), dp(24)); setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val scroll = ScrollView(this); scroll.addView(root); setContentView(scroll)
        root.addView(text(title, 27f, green, true), params(bottom = 8))
        root.addView(text(subtitle, 14f, Color.GRAY), params(bottom = 22))
    }

    private fun showError(message: String) {
        setupScreen("تحدي جدول الضرب", "منافسة مباشرة بين الأصدقاء")
        root.addView(text(message, 17f, Color.rgb(180, 50, 50), true), params(top = 20))
        val retry = btn("إعادة المحاولة")
        root.addView(retry, params(top = 20)); retry.setOnClickListener { recreate() }
    }

    private fun loadProfile() {
        db.getReference("profiles").child(uid).get().addOnSuccessListener { snap ->
            playerName = snap.child("name").getValue(String::class.java) ?: ""
            avatarUrl = snap.child("avatarUrl").getValue(String::class.java) ?: ""
            if (playerName.isBlank()) showProfileSetup() else showHome()
        }.addOnFailureListener { showProfileSetup() }
    }


    private fun loadAvatar(url: String, image: ImageView) {
        if (url.isBlank()) return
        Thread {
            try {
                val bitmap = URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) runOnUiThread { image.setImageBitmap(bitmap) }
            } catch (_: Exception) {}
        }.start()
    }

    private fun showProfileSetup() {
        setupScreen("ملف اللاعب", "اختر اسماً وصورة تظهر للأصدقاء")
        val avatarPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply { bottomMargin = dp(14) }
            background = rounded(pale, 48)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(avatarPreview)
        loadAvatar(avatarUrl, avatarPreview)
        profileNameInput = input("اسم اللاعب")
        profileNameInput?.setText(playerName)
        root.addView(profileNameInput!!, params(bottom = 12))
        val photo = btn("اختيار صورة شخصية")
        root.addView(photo, params(bottom = 10))
        profileStatus = text(if (avatarUrl.isBlank()) "لم تختر صورة بعد" else "تم حفظ صورة شخصية", 14f, Color.GRAY)
        root.addView(profileStatus!!, params(bottom = 18))
        photo.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(intent, PICK_IMAGE)
        }
        val save = btn("حفظ الملف الشخصي")
        root.addView(save, params())
        save.setOnClickListener {
            val name = profileNameInput?.text?.toString()?.trim().orEmpty()
            if (name.length < 2) { profileNameInput?.error = "اكتب اسماً من حرفين على الأقل"; return@setOnClickListener }
            playerName = name
            saveProfile(pendingAvatarUri)
        }
    }

    @Deprecated("Using legacy picker callback for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            pendingAvatarUri = data?.data
            profileStatus?.text = if (pendingAvatarUri != null) "تم اختيار الصورة، احفظ الملف لرفعها" else "لم يتم اختيار صورة"
        }
    }

    private fun saveProfile(imageUri: Uri?) {
        profileStatus?.text = "جارٍ حفظ الملف..."
        val finishSave: (String) -> Unit = { url ->
            avatarUrl = url
            val ref = db.getReference("profiles").child(uid)
            ref.child("name").setValue(playerName)
            ref.child("avatarUrl").setValue(avatarUrl)
            ref.child("updatedAt").setValue(ServerValue.TIMESTAMP)
            ref.get().addOnSuccessListener { snap ->
                if (!snap.hasChild("totalPoints")) ref.child("totalPoints").setValue(0)
                if (!snap.hasChild("gamesPlayed")) ref.child("gamesPlayed").setValue(0)
                if (!snap.hasChild("wins")) ref.child("wins").setValue(0)
                showHome()
            }.addOnFailureListener { showError("تعذر حفظ الملف الشخصي: ${it.localizedMessage}") }
        }
        if (imageUri == null) {
            finishSave(avatarUrl)
        } else {
            val imageRef = storage.reference.child("avatars/$uid/profile.jpg")
            imageRef.putFile(imageUri).addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { finishSave(it.toString()) }
                    .addOnFailureListener { showError("تعذر الحصول على رابط الصورة: ${it.localizedMessage}") }
            }.addOnFailureListener { showError("فشل رفع الصورة. تأكد من تفعيل Firebase Storage: ${it.localizedMessage}") }
        }
    }

    private fun showHome() {
        setupScreen("🏆 تحدي جدول الضرب", "أهلاً $playerName — نافس أصدقاءك أونلاين")
        val homeAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76)).apply { bottomMargin = dp(10) }
            background = rounded(pale, 40)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(homeAvatar)
        loadAvatar(avatarUrl, homeAvatar)
        val profile = btn("تعديل الاسم أو الصورة")
        root.addView(profile, params(bottom = 10))
        profile.setOnClickListener { showProfileSetup() }
        val create = btn("إنشاء غرفة جديدة")
        root.addView(create, params(bottom = 12))
        val code = input("أدخل رمز الغرفة")
        root.addView(code, params(bottom = 12))
        val join = btn("الانضمام إلى غرفة")
        root.addView(join, params(bottom = 12))
        val leaderboard = btn("🏅 لوحة المتصدرين")
        root.addView(leaderboard, params(bottom = 18))
        val stats = text("جارٍ تحميل إحصائياتك...", 15f, green, true)
        root.addView(stats, params(bottom = 14))
        db.getReference("profiles").child(uid).get().addOnSuccessListener { snap ->
            val points = snap.child("totalPoints").getValue(Int::class.java) ?: 0
            val games = snap.child("gamesPlayed").getValue(Int::class.java) ?: 0
            val wins = snap.child("wins").getValue(Int::class.java) ?: 0
            stats.text = "نقاطك: $points  •  المباريات: $games  •  الانتصارات: $wins"
        }
        root.addView(text("طريقة اللعب", 19f, green, true), params(bottom = 8))
        root.addView(text("أنشئ غرفة وشارك الرمز مع أصدقائك. كل لاعب يجيب عن 20 سؤالاً، ونقطة واحدة لكل إجابة صحيحة. تظهر النتائج والترتيب بعد انتهاء الجولة.", 15f), params())
        create.setOnClickListener { createRoom() }
        join.setOnClickListener {
            roomCode = code.text.toString().trim().uppercase(Locale.ROOT)
            if (roomCode.length != 6) { code.error = "الرمز يتكون من 6 أحرف أو أرقام"; return@setOnClickListener }
            joinRoom()
        }
        leaderboard.setOnClickListener { showLeaderboard() }
    }

    private fun showLeaderboard() {
        setupScreen("🏅 لوحة المتصدرين", "أفضل اللاعبين حسب مجموع النقاط")
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, params())
        list.addView(text("جارٍ تحميل المتصدرين...", 16f, Color.GRAY))
        db.getReference("profiles").orderByChild("totalPoints").limitToLast(50).get()
            .addOnSuccessListener { snap ->
                list.removeAllViews()
                val players = snap.children.map { s ->
                    Triple(s.child("name").getValue(String::class.java) ?: "لاعب",
                        s.child("totalPoints").getValue(Int::class.java) ?: 0,
                        s.child("wins").getValue(Int::class.java) ?: 0)
                }.sortedByDescending { it.second }
                if (players.isEmpty()) list.addView(text("لا توجد نتائج بعد. كن أول المتصدرين!", 16f))
                players.forEachIndexed { i, p ->
                    val uidForRow = snap.children.firstOrNull { it.child("name").getValue(String::class.java) == p.first }?.key
                    val avatar = uidForRow?.let { snap.child(it).child("avatarUrl").getValue(String::class.java) } ?: ""
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                    }
                    val pic = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) }
                        background = rounded(pale, 22)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    loadAvatar(avatar, pic)
                    row.addView(pic)
                    val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }
                    row.addView(text("$medal ${p.first}  •  ${p.second} نقطة  •  ${p.third} فوز", 15f, dark, i < 3),
                        LinearLayout.LayoutParams(0, -2, 1f))
                    list.addView(row, params(top = 8))
                }
            }.addOnFailureListener { list.addView(text("تعذر تحميل القائمة: ${it.localizedMessage}", 14f, Color.RED)) }
        val back = btn("رجوع")
        root.addView(back, params(top = 20))
        back.setOnClickListener { showHome() }
    }

    private fun createRoom() {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        roomCode = (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        val qs = (3..9).flatMap { a -> (1..10).map { b -> mapOf("a" to a, "b" to b) } }.shuffled().take(20)
        val ref = db.getReference("rooms").child(roomCode)
        val room = mapOf(
            "hostId" to uid, "status" to "waiting", "questionIndex" to 0,
            "questions" to qs, "createdAt" to ServerValue.TIMESTAMP,
            "players" to mapOf(uid to mapOf("name" to playerName, "avatarUrl" to avatarUrl,
                "score" to 0, "answeredIndex" to -1))
        )
        ref.setValue(room).addOnSuccessListener { isHost = true; savedResultForRoom = false; attachRoom() }
            .addOnFailureListener { showError("تعذر إنشاء الغرفة: ${it.localizedMessage}") }
    }

    private fun joinRoom() {
        val ref = db.getReference("rooms").child(roomCode)
        ref.get().addOnSuccessListener { snap ->
            if (!snap.exists()) { toast("رمز الغرفة غير موجود"); return@addOnSuccessListener }
            val status = snap.child("status").getValue(String::class.java)
            if (status != "waiting") { toast("اللعبة بدأت بالفعل أو انتهت"); return@addOnSuccessListener }
            ref.child("players").child(uid).setValue(mapOf("name" to playerName, "avatarUrl" to avatarUrl,
                "score" to 0, "answeredIndex" to -1)).addOnSuccessListener {
                isHost = false; savedResultForRoom = false; attachRoom()
            }.addOnFailureListener { showError("تعذر الانضمام: ${it.localizedMessage}") }
        }.addOnFailureListener { showError("تعذر الاتصال: ${it.localizedMessage}") }
    }

    private fun attachRoom() {
        detachRoom()
        roomRef = db.getReference("rooms").child(roomCode)
        setupRoomScreen()
        roomListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) { showError("الغرفة غير موجودة أو تم حذفها."); return }
                currentStatus = snapshot.child("status").getValue(String::class.java) ?: "waiting"
                currentQ = snapshot.child("questionIndex").getValue(Int::class.java) ?: 0
                val players = snapshot.child("players")
                updatePlayers(players)
                when (currentStatus) {
                    "waiting" -> {
                        roomStatus?.text = "رمز الغرفة: $roomCode\nأرسل الرمز لأصدقائك"
                        roomQuestion?.text = "بانتظار اللاعبين..."
                        roomAnswer?.visibility = View.GONE
                        roomFeedback?.text = ""
                        roomAction?.visibility = if (isHost) View.VISIBLE else View.GONE
                        roomAction?.text = "بدء المنافسة"
                    }
                    "playing" -> {
                        val q = snapshot.child("questions").child(currentQ.toString())
                        val a = q.child("a").getValue(Int::class.java) ?: 0
                        val b = q.child("b").getValue(Int::class.java) ?: 0
                        val answered = players.child(uid).child("answeredIndex").getValue(Int::class.java) ?: -1
                        localAnswered = answered == currentQ
                        roomStatus?.text = "الغرفة: $roomCode  •  السؤال ${currentQ + 1} من 20"
                        roomQuestion?.text = "$a × $b = ؟"
                        roomAnswer?.visibility = View.VISIBLE
                        roomAction?.visibility = View.VISIBLE
                        roomAnswer?.isEnabled = !localAnswered
                        roomAction?.isEnabled = if (isHost && localAnswered) true else !localAnswered
                        roomAction?.text = when {
                            isHost && localAnswered -> "السؤال التالي للجميع"
                            localAnswered -> "بانتظار صاحب الغرفة"
                            else -> "إرسال الإجابة"
                        }
                    }
                    "finished" -> {
                        saveMatchResults(players)
                        showResults(players)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { toast(error.message) }
        }
        roomRef!!.addValueEventListener(roomListener!!)
    }

    private var roomStatus: TextView? = null
    private var roomQuestion: TextView? = null
    private var roomAnswer: EditText? = null
    private var roomAction: Button? = null
    private var roomFeedback: TextView? = null
    private var playersList: LinearLayout? = null

    private fun setupRoomScreen() {
        setupScreen("غرفة المنافسة", "شارك الرمز مع أصدقائك")
        roomStatus = text("", 18f, green, true); root.addView(roomStatus!!, params(bottom = 14))
        root.addView(text("اللاعبون", 18f, dark, true), params(bottom = 8))
        playersList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(pale, 14); setPadding(dp(12), dp(10), dp(12), dp(10)) }
        root.addView(playersList!!, params(bottom = 16))
        roomQuestion = text("...", 29f, dark, true); root.addView(roomQuestion!!, params(top = 10, bottom = 16))
        roomAnswer = input("اكتب الإجابة", true); root.addView(roomAnswer!!, params(bottom = 10))
        roomFeedback = text("", 15f, green, true); root.addView(roomFeedback!!, params(bottom = 10))
        roomAction = btn("بدء المنافسة"); root.addView(roomAction!!, params(bottom = 10))
        roomAction!!.setOnClickListener { onRoomAction() }
        val leave = btn("العودة للرئيسية"); root.addView(leave, params(top = 8))
        leave.setOnClickListener {
            roomRef?.child("players")?.child(uid)?.removeValue()
            detachRoom(); showHome()
        }
    }

    private fun updatePlayers(players: DataSnapshot) {
        playersList?.removeAllViews()
        val rows = players.children.map { s ->
            val name = s.child("name").getValue(String::class.java) ?: "لاعب"
            val score = s.child("score").getValue(Int::class.java) ?: 0
            val answered = s.child("answeredIndex").getValue(Int::class.java) ?: -1
            val avatar = s.child("avatarUrl").getValue(String::class.java) ?: ""
            listOf(name, score, answered, avatar)
        }.sortedByDescending { it[1] as Int }
        rows.forEachIndexed { i, p ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            val pic = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(8) }
                background = rounded(Color.WHITE, 19)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            loadAvatar(p[3] as String, pic)
            row.addView(pic)
            row.addView(text("${i + 1}. ${p[0]} — ${p[1]} نقطة${if (p[2] == currentQ) " ✓" else ""}", 14f),
                LinearLayout.LayoutParams(0, -2, 1f))
            playersList?.addView(row)
        }
    }

    private fun onRoomAction() {
        val ref = roomRef ?: return
        if (currentStatus == "waiting" && isHost) { ref.child("status").setValue("playing"); return }
        if (currentStatus != "playing") return
        if (localAnswered && isHost) {
            if (currentQ >= 19) ref.child("status").setValue("finished")
            else ref.child("questionIndex").setValue(currentQ + 1)
            return
        }
        if (localAnswered) return
        val raw = roomAnswer?.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) { roomAnswer?.error = "اكتب الإجابة"; return }
        ref.child("questions").child(currentQ.toString()).get().addOnSuccessListener { q ->
            val a = q.child("a").getValue(Int::class.java) ?: 0
            val b = q.child("b").getValue(Int::class.java) ?: 0
            val correct = raw.toIntOrNull() == a * b
            ref.child("players").child(uid).runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val answered = data.child("answeredIndex").getValue(Int::class.java) ?: -1
                    if (answered == currentQ) return Transaction.abort()
                    val oldScore = data.child("score").getValue(Int::class.java) ?: 0
                    data.child("answeredIndex").value = currentQ
                    data.child("lastCorrect").value = correct
                    if (correct) data.child("score").value = oldScore + 1
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (committed) {
                        localAnswered = true
                        roomFeedback?.text = if (correct) "إجابة صحيحة 👏" else "الإجابة الصحيحة: ${a * b}"
                        roomAnswer?.isEnabled = false
                        roomAction?.text = if (isHost) "السؤال التالي للجميع" else "بانتظار صاحب الغرفة"
                        roomAction?.isEnabled = isHost
                    } else roomFeedback?.text = "تم تسجيل إجابتك مسبقاً."
                }
            })
        }
    }

    private fun saveMatchResults(players: DataSnapshot) {
        if (savedResultForRoom) return
        savedResultForRoom = true
        val me = players.child(uid)
        val myScore = me.child("score").getValue(Int::class.java) ?: 0
        val maxScore = players.children.map { it.child("score").getValue(Int::class.java) ?: 0 }.maxOrNull() ?: 0
        val isWinner = myScore == maxScore && maxScore > 0
        val profileRef = db.getReference("profiles").child(uid)
        profileRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val points = data.child("totalPoints").getValue(Int::class.java) ?: 0
                val games = data.child("gamesPlayed").getValue(Int::class.java) ?: 0
                val wins = data.child("wins").getValue(Int::class.java) ?: 0
                data.child("totalPoints").value = points + myScore
                data.child("gamesPlayed").value = games + 1
                if (isWinner) data.child("wins").value = wins + 1
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    private fun showResults(players: DataSnapshot) {
        setupScreen("🏆 انتهت المنافسة!", "الغرفة $roomCode")
        val rows = players.children.map { s ->
            (s.child("name").getValue(String::class.java) ?: "لاعب") to
                (s.child("score").getValue(Int::class.java) ?: 0)
        }.sortedByDescending { it.second }
        rows.forEachIndexed { i, p ->
            val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }
            root.addView(text("$medal ${p.first} — ${p.second} / 20", 18f, dark, true), params(top = 10))
        }
        val back = btn("العودة للرئيسية"); root.addView(back, params(top = 28))
        back.setOnClickListener { detachRoom(); showHome() }
    }

    private fun detachRoom() {
        roomRef?.let { r -> roomListener?.let { r.removeEventListener(it) } }
        roomRef = null; roomListener = null
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    override fun onDestroy() { detachRoom(); super.onDestroy() }
}
