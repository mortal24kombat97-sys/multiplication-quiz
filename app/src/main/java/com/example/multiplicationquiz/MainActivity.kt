package com.example.multiplicationquiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlin.random.Random

data class Player(val name: String = "", val score: Int = 0)
data class Room(
    val player1: Player = Player(),
    val player2: Player = Player(),
    val num1: Int = 2,
    val num2: Int = 3,
    val round: Int = 1,
    val status: String = "waiting",
    val winner: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseAuth.getInstance().signInAnonymously()
        setContent { OnlineGameApp() }
    }
}

@Composable
fun OnlineGameApp() {
    var myName by remember { mutableStateOf("") }
    var roomCode by remember { mutableStateOf("") }
    var room by remember { mutableStateOf<Room?>(null) }
    var isPlayer1 by remember { mutableStateOf(true) }
    var answer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val db = FirebaseDatabase.getInstance().reference

    fun listenToRoom(code: String) {
        db.child("rooms").child(code).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                room = s.getValue(Room::class.java)
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    fun nextQuestion(code: String, current: Room) {
        if (current.round >= 10) {
            val winner = if (current.player1.score > current.player2.score) current.player1.name else current.player2.name
            db.child("rooms").child(code).child("status").setValue("finished")
            db.child("rooms").child(code).child("winner").setValue(winner)
        } else {
            db.child("rooms").child(code).child("num1").setValue(Random.nextInt(2, 10))
            db.child("rooms").child(code).child("num2").setValue(Random.nextInt(2, 10))
            db.child("rooms").child(code).child("round").setValue(current.round + 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (room == null) {
            Text("لعبة جدول الضرب - اونلاين 🔥", fontSize = 26.sp)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(value = myName, onValueChange = { myName = it }, label = { Text("اسمك") })
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = roomCode, onValueChange = { roomCode = it }, label = { Text("كود الغرفة") })
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                val code = (1000..9999).random().toString()
                roomCode = code
                isPlayer1 = true
                val newRoom = Room(player1 = Player(myName, 0), status = "waiting")
                db.child("rooms").child(code).setValue(newRoom)
                listenToRoom(code)
            }) { Text("انشاء غرفة جديدة") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                isPlayer1 = false
                db.child("rooms").child(roomCode).child("player2").setValue(Player(myName, 0))
                db.child("rooms").child(roomCode).child("status").setValue("playing")
                listenToRoom(roomCode)
            }) { Text("دخول غرفة صديقك") }
            if (roomCode.isNotEmpty()) Text("كودك: $roomCode دزه لصديقك", fontSize = 18.sp)
        } else if (room!!.status == "waiting") {
            Text("انتظار اللاعب الثاني...", fontSize = 22.sp)
            Text("كود الغرفة: $roomCode", fontSize = 24.sp)
        } else if (room!!.status == "finished") {
            Text("انتهت اللعبة! 🏆", fontSize = 30.sp)
            Text("الفائز: ${room!!.winner}", fontSize = 26.sp)
            Text("${room!!.player1.name}: ${room!!.player1.score} - ${room!!.player2.name}: ${room!!.player2.score}", fontSize = 20.sp)
            Button(onClick = { room = null }) { Text("العب مرة ثانية") }
        } else {
            val r = room!!
            Text("الجولة ${r.round} / 10", fontSize = 18.sp)
            Text("${r.player1.name}: ${r.player1.score}  |  ${r.player2.name}: ${r.player2.score}", fontSize = 18.sp)
            Spacer(Modifier.height(20.dp))
            Text("${r.num1} × ${r.num2} = ?", fontSize = 45.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("جوابك") })
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                if (answer.toIntOrNull() == r.num1 * r.num2) {
                    message = "صح! +1 ✅"
                    val path = if (isPlayer1) "player1/score" else "player2/score"
                    val newScore = if (isPlayer1) r.player1.score + 1 else r.player2.score + 1
                    db.child("rooms").child(roomCode).child(path).setValue(newScore)
                    if (isPlayer1) nextQuestion(roomCode, r.copy(player1 = r.player1.copy(score = newScore)))
                    answer = ""
                } else {
                    message = "غلط! حاول مرة ثانية ❌"
                }
            }) { Text("جاوب") }
            Text(message)
        }
    }
}
