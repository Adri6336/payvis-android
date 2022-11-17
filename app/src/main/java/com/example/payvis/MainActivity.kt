// Note: will need to create or edit file every second to protect data
// Icon made with money image found here: https://www.pngarts.com/files/3/Falling-Cash-Money-PNG-Photo.png
package com.example.payvis

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.google.gson.Gson

data class Time(val initialTime: LocalDateTime){
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val startTime = initialTime.format(formatter)
    var timeAsSeconds: Double = 0.0  // This will be replaced by a value after init

    // The following values are slices from startTime string. I can think of a better way
    // to do it, but this is already here and it works so it'll stay
    val year = startTime.slice(0..3).toInt()  // here just in case I need it
    val month = startTime.slice(5..6).toInt()
    val day = startTime.slice(8..9).toInt()
    val hour = startTime.slice(11..12).toInt()
    val minute = startTime.slice(14..15).toInt()
    val second = startTime.slice(17..18).toInt()

    val dbEntryTag = startTime.split(" ")[0]  // Grabs the year, month, and day

    init {
        // To get the time as seconds, total up the num sec in month, day, ... sec
        val monthSec = this.month.toDouble() * 2628002.88
        val daySec = this.day.toDouble() * 86400.0
        val hourSec = this.hour * 3600.0
        val minSec = this.minute * 60.0
        val secSec = this.second.toDouble()

        this.timeAsSeconds = (monthSec + daySec + hourSec + minSec + secSec)
    }

    fun getElapsedTimeSec(endTime: LocalDateTime): Double{
        // This returns the elapsed time between the initialTime and endTime LocalDateTime

        val end = Time(endTime).timeAsSeconds
        return (end - this.timeAsSeconds)
    }

    fun getElapsedTimeAsString(endTime: LocalDateTime): String{
        // Returns the time that elapsed between start and endTime as string

        // 1. Get the hour and minute count of time passed
        val elapsed = this.getElapsedTimeSec(endTime)

        // 1.01  Get around bug that occurs with time < 1 min
        if (elapsed < 60 && elapsed > 0){
            return "$elapsed seconds"
        }
        else if (elapsed < 1 && elapsed > -1){  // Catch infinitesimals
            return "No time passed"
        }

        var timeElapsed = ((elapsed / 60.0) / 60.0).toString().split(".")  //place 0 = hours, 1 = minutes
        val hours =  timeElapsed[0].toInt()
        var minutes = ("." + timeElapsed[1]).toDouble() * 60  // re-add the period before converting to d

        // 1.1 Format minutes to have at most 2 decimal places
        minutes = String.format("%.02f", minutes).toDouble()

        // 2. Create two strings with the hour and minute data
        var hourText: String
        var minText: String

        if (hours > 1){hourText = "$hours hours"}  // If there's more than one, it should be plural
        else if (hours == 1) {hourText = "$hours hour"}
        else {hourText = ""}

        if (minutes > 1.0){minText = "$minutes minutes"}
        else if (minutes == 1.0){minText = "$minutes minute"}
        else {minText = ""}

        // 3. Compile final string
        if (hours == 0 && minutes == 0.0){return ""}
        else if (hours == 0 && minutes > 0.0){return "$minText"}
        else if (hours > 0 && minutes == 0.0) {return "$hourText"}
        else {return "$hourText and $minText"}
    }
}

data class Clock(val startTime: Time){
    var totalSeconds: Double = 0.0
    var active = true
    var dataBase: MutableMap<String, Map<String, String>> = mutableMapOf()  // day : total sec, pay, start, and end times

    fun update(){
        val now = LocalDateTime.now()
        this.totalSeconds = startTime.getElapsedTimeSec(now)
    }

    fun stop(){
        this.active = false
    }

    fun calculatePay(payRate: Double, secondsWorked: Double): Double{
        val payPerSec = ((payRate / 60.0) / 60.0)
        return (payPerSec * secondsWorked)
    }

    fun createDBEntry(payRate: Double){
        // This will create a map entry containing time worked and pay earned

        // 1. Get entry key
        val day = startTime.dbEntryTag

        // 2. Create entry value
        val end = this.update()  // Grabs the finishing time
        val pay = this.calculatePay(payRate, this.totalSeconds)
        val data: Map<String, String> = mapOf(
            "seconds" to this.totalSeconds.toString(),
            "pay" to pay.toString(),
            "start" to this.startTime.toString(),
            "finish" to end.toString()
        )

        // 3. Create entry in db
        dataBase.put(day, data)
    }

    fun makeJsonString(): String{
        // Creates a json string of the database mutable map
        val gson = Gson()
        val dbString: String = gson.toJson(this.dataBase, MutableMap::class.java)
        return dbString
    }

    fun loadJsonString(data: String){
        val gson = Gson()
        val db = gson.fromJson(data, MutableMap::class.java)
    }

}


class MainActivity : AppCompatActivity() {

    // ============ FUNCTIONS ============

    fun goTo(url: String){
        // Opens browser pointing to url passed
        // https://stackoverflow.com/questions/5026349/how-to-open-a-website-when-a-button-is-clicked-in-android-application
        val uriUrl = Uri.parse(url)
        ContextCompat.startActivity(this, Intent(Intent.ACTION_VIEW, uriUrl), null)
    }

    fun saveFile(content: String, fileName: String){
        File(applicationContext.filesDir, fileName).printWriter().use{
            out -> out.println(content)
        }
    }

    fun readFile(fileName: String): String{
        File(applicationContext.filesDir, fileName).bufferedReader().use{
            return it.readText()
        }
    }

    fun clockFileExists(): Boolean{
        // Determines if the clock file has been created
        return File("clock.pvcf").exists()
    }

    fun clockFileFormattedCorrect(): Boolean{
        // Determines if the clock file was correctly formatted
        // If the data can be safely extracted, it should be formatted correctly
        try{
            val clockData = readFile("clock.pvcf").split("\n")
            val timeStamp = LocalDateTime.parse(clockData[0])
            val totalSec = clockData[1].toDouble()
            val active = clockData[2].toBoolean()
            return true

        } catch (t: Throwable){
            return false
        }
    }

    fun createClockFile(clock: Clock){
        val timeStamp = clock.startTime.initialTime.toString()
        val totalSeconds = clock.update()
        val active = clock.active
        val dataStr: String = "$timeStamp\n$totalSeconds\n$active"
        this.saveFile(dataStr, "clock.pvcf")
    }

    fun loadClockFile(): Clock{
        /*
        Opens the clock file, grabs the data within, and creates a new
        Clock object with it. Returns this new object
         */

        // 1. Get data from file
        val clockData = readFile("clock.pvcf").split("\n")
        val timeStamp = LocalDateTime.parse(clockData[0])
        val totalSec = clockData[1].toDouble()
        val active = clockData[2].toBoolean()

        // 2. Create objects
        val startTime: Time = Time(timeStamp)
        val newClock: Clock = Clock(startTime)
        newClock.totalSeconds = totalSec
        newClock.active = active

        return newClock
    }

    fun saveDBFile(clock: Clock){
        // Grabs the mutable map json string from Clock object and saves it to a file
        val dbJson = clock.makeJsonString()
        saveFile(dbJson, "workDB.json")
    }

    fun loadDBFile(clock: Clock){
        val dbJson = readFile("workDB.json")
        clock.loadJsonString(dbJson)
    }

    // ============ APP ============
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Connect elements from app
        val startButton = findViewById<Button>(R.id.start_button)
        val resetButton = findViewById<Button>(R.id.reset_time_button)
        val displayPayButton = findViewById<Button>(R.id.display_pay_button)

        val notifyView = findViewById<TextView>(R.id.notify_view)
        val timeWorked = findViewById<TextView>(R.id.time_worked_view)
        val payView = findViewById<TextView>(R.id.pay_view)

        val rateEntry = findViewById<EditText>(R.id.current_wage_entry)


        // Create Listeners
        startButton.setOnClickListener{

        }

    }
}
