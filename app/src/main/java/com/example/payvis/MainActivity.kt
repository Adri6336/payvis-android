// Note: will need to create or edit file every second to protect data
// Icon made with money image found here: https://www.pngarts.com/files/3/Falling-Cash-Money-PNG-Photo.png
package com.example.payvis

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.google.gson.Gson
import java.net.URI

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
        val difference = end - this.timeAsSeconds
        println("DIFFERENCE: $difference")

        if (difference > 0.0){return difference}
        else {return 0.0}
    }

    fun statement(timePassed: Number, timeUnit: String): String{

        val singular = when (timeUnit){
            "hour" -> "hour"
            "min" -> "minute"
            "sec" -> "second"
            else -> "$timeUnit"
        }

        val plural = when(timeUnit){
            "hour" -> "hours"
            "min" -> "minutes"
            "sec" -> "seconds"
            else -> "${timeUnit}s"
        }

        var timeText: String

        if (timePassed.toInt() > 1) {timeText = "$timePassed $plural"}  // If there's more than one, it should be plural
        else if (timePassed.toInt() == 1) {timeText = "$timePassed $singular"}
        else {timeText = ""}

        return timeText
    }

    fun getElapsedTimeAsString(endTime: LocalDateTime, elapSec: Double = -404.4): String{
        // Returns the time that elapsed between start and endTime as string

        // 1. Get the hour and minute count of time passed
        val elapsed = when(elapSec){  // If we passed a value to elapSec, use it instead of endTime
            -404.4 -> this.getElapsedTimeSec(endTime)
            else -> elapSec
        }

        // 1.01  Get around bug that occurs with time < 1 min
        if (elapsed < 60 && elapsed > 0){
            return "${this.statement(elapsed.toInt(), "sec")} worked"
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

        hourText = this.statement(hours, "hour")
        minText = this.statement(minutes, "min")


        // 3. Compile final string
        if (hours == 0 && minutes == 0.0){return ""}
        else if (hours == 0 && minutes > 0.0){return "$minText worked"}
        else if (hours > 0 && minutes == 0.0) {return "$hourText worked"}
        else {return "$hourText and $minText worked"}
    }
}

data class Clock(val startTime: Time){
    var totalSeconds: Double = 0.0
    var sessionSeconds: Double = 0.0
    var active = false
    var dataBase: MutableMap<String, Map<String, String>> = mutableMapOf()  // day : total sec, pay, start, and end times
    var rate: Double = 0.0
    var pay: Double = 0.0

    fun update(): LocalDateTime{
        val now = LocalDateTime.now()
        this.sessionSeconds = startTime.getElapsedTimeSec(now)
        return now
    }

    fun stop(){
        this.active = false
    }

    fun calculatePay(payRate: Double, secondsWorked: Double): Double{
        val payPerSec = ((payRate / 60.0) / 60.0)
        return (payPerSec * secondsWorked)
    }

    fun createDBEntry(payRate: Double, endTime: Time){
        /*
        This will create a map entry containing time worked and pay earned

        Note: the clock must be fully updated before this is called,
        or the database will receive an inaccurate entry
        */
        // 1. Get entry key
        val day = startTime.dbEntryTag

        // 2. Create entry value
        val end = endTime
        val pay = this.calculatePay(payRate, this.totalSeconds)
        val data: Map<String, String> = mapOf(
            "seconds" to this.totalSeconds.toString(),  // Total seconds should be updated before fun called
            "pay" to pay.toString(),  // This allows pay to accumulate over sessions
            "start" to this.startTime.startTime,
            "finish" to end.startTime
        )

        // 3. Create entry in db
        println("[i] DB BEFORE ADD: ${this.dataBase}")
        this.dataBase.put(day, data)
        println("[i] DB WITH ADD: ${this.dataBase}")
    }

    fun makeJsonString(): String{
        // Creates a json string of the database mutable map
        val gson = Gson()
        val dbString: String = gson.toJson(this.dataBase, this.dataBase.javaClass)
        return dbString
    }

    fun loadJsonString(data: String){
        val gson = Gson()
        val db = gson.fromJson(data, this.dataBase.javaClass)
        this.dataBase = db
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
        val result = File(applicationContext.filesDir, "clock.pvcf").exists()
        println("[i] Clock file test result: $result")
        return result
    }

    fun clockFileFormattedCorrect(): Boolean{
        // Determines if the clock file was correctly formatted
        // If the data can be safely extracted, it should be formatted correctly
        try{
            val clockData = readFile("clock.pvcf").split("\n")
            val timeStamp = LocalDateTime.parse(clockData[0])
            val totalSec = clockData[1].toDouble()
            val active = clockData[2].toBoolean()
            val rate = clockData[3].toDouble()
            val sessionSeconds = clockData[4].toDouble()

            println("[i] Clock file formatted correctly")
            return true

        } catch (t: Throwable){
            println("[X] Clock file formatted incorrectly")
            return false
        }
    }

    fun createClockFile(clock: Clock){
        val timeStamp = clock.startTime.initialTime.toString()
        val totalSeconds = clock.totalSeconds
        val active = clock.active
        val rate = clock.rate
        val sessionSeconds = clock.sessionSeconds
        val dataStr: String = "$timeStamp\n$totalSeconds\n$active\n$rate\n$sessionSeconds"

        try{
            this.saveFile(dataStr, "clock.pvcf")
        } catch (t: Throwable){
            println("[X] Clock file failed to save correctly")
        }
    }

    fun saveDBFile(clock: Clock){
        // Grabs the mutable map json string from Clock object and saves it to a file
        val dbJson = clock.makeJsonString()
        saveFile(dbJson, "workDB.json")
    }

    fun loadDBFile(clock: Clock){
        val dbJson = readFile("workDB.json")
        println("[i] Found: $dbJson")
        clock.loadJsonString(dbJson)
        println("[i] Loaded: ${clock.dataBase}")
    }

    fun setupDB(){
        /*
        This will test the db file. If something is wrong with it, it will create a new,
        blank db file.
         */
        // Variables
        var dbExists = false
        var dbFormattedCorrectly = false
        var dataBaseSample: MutableMap<String, Map<String, String>> = mutableMapOf()
        var dbJsonString = ""
        val gson = Gson()

        // 1. Test existence
        dbExists = File(applicationContext.filesDir, "workDB.json").exists()

        // 2. Test if formatted correctly

        try{
            dbJsonString = readFile("workDB.json")
            dataBaseSample = gson.fromJson(dbJsonString, dataBaseSample.javaClass)
            dbFormattedCorrectly = true

        } catch (t: Throwable){
            println("[X] DB FORMAT ERROR: $t")
        }

        // 3. Return if everything is okay, or remediate
        dataBaseSample = mutableMapOf()  // This should wipe the db

        if (dbExists && dbFormattedCorrectly){
            return
        } else {
            dbJsonString = gson.toJson(dataBaseSample, dataBaseSample.javaClass)
            saveFile(dbJsonString, "workDB.json")
        }
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
        val rate = clockData[3].toDouble()
        val sessionSeconds = clockData[4].toDouble()

        // 2. Create objects
        val startTime: Time = Time(timeStamp)
        val newClock: Clock = Clock(startTime)
        newClock.totalSeconds = totalSec
        newClock.active = active
        newClock.rate = rate
        newClock.sessionSeconds = sessionSeconds
        loadDBFile(newClock)

        return newClock
    }


    fun clockActive(): Boolean{
        // Determines if clock active
        val clockData = readFile("clock.pvcf").split("\n")
        try{
            return clockData[2].toBoolean()
        } catch (t: Throwable){
            return false
        }
    }

    fun getRate(): Double{
        val rate: Double
        val notifyView = findViewById<TextView>(R.id.notify_view)
        val rateEntry = findViewById<EditText>(R.id.current_wage_entry)

        try{
            rate = rateEntry.text.toString().toDouble()
            notifyView.text = ""  // If there was previously an error, this wipes it
            return rate
        } catch(e: java.lang.NumberFormatException){
            notifyView.text = "Please enter a number into \"Your Wage\""
            return -404.4
        } catch (t: Throwable){
            notifyView.text = "ERROR: ${t.message}"
            return -404.4
        }
    }


    // ============ APP ============
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Connect elements from app
        val startButton = findViewById<Button>(R.id.start_button)
        val resetButton = findViewById<Button>(R.id.reset_time_button)
        val displayPayButton = findViewById<Button>(R.id.display_pay_button)
        val exportDataButton = findViewById<Button>(R.id.export_button)

        val notifyView = findViewById<TextView>(R.id.notify_view)
        val timeWorked = findViewById<TextView>(R.id.time_worked_view)
        val payView = findViewById<TextView>(R.id.pay_view)

        val rateEntry = findViewById<EditText>(R.id.current_wage_entry)

        // Clear default text
        notifyView.text = ""
        timeWorked.text = ""
        payView.text = ""

        // Other Variables
        var startButtonPressed = false  // This will be used to reset ct if count started

        // Setup DataBase
        setupDB()

        // Determine if clock file exists
        var clockStarted: Boolean  // If this remains false, app will make a new clock file at start
        var clock: Clock = Clock(Time(LocalDateTime.now()))  // Set equal to a place holder
        var date = Time(LocalDateTime.now()).startTime.split(" ")[0]
        // The above gets the current time and grabs the date section (time and date separated by space)
        var fileDate: String  // This is the date found in the file
        var now = LocalDateTime.now()
        var rate: Double
        var payDisp: Double

        if (clockFileExists() && clockFileFormattedCorrect() && clockActive()) {  // Active clock
            // Mark clock started and import settings
            println("[i] Active clock detected")
            clock = loadClockFile()

            rateEntry.setText("${String.format("%.02f", clock.rate)}")  // Loads saved rate to entry
            rate = getRate()
            timeWorked.text = "${clock.startTime.getElapsedTimeAsString(now)}"
            payDisp = clock.calculatePay(rate, clock.startTime.getElapsedTimeSec(now))
            payView.text = "$${String.format("%.02f", payDisp)} earned"

            clockStarted = true

        } else if (clockFileExists() && clockFileFormattedCorrect() && !clockActive()){
            // If the file is good and ready, but the clock was stopped
            println("[i] Inactive but valid clock detected")

            // 1. Load permanent data
            clock = loadClockFile()
            rateEntry.setText(String.format("%.02f", clock.rate))
            fileDate = clock.startTime.startTime.split(" ")[0]
            println("FILEDATE: $fileDate\nDATE: $date\nequal = ${fileDate == date}")

            // 2. If last active was today, keep total seconds
            if (fileDate != date){
                clock.totalSeconds = 0.0
            }

            // 3. Wipe session seconds regardless
            clock.sessionSeconds = 0.0
            clockStarted = false

        } else{
            println("No valid clock detected")
            clockStarted = false
        }

        // Create Listeners
        // ===========================================================
        // ================Start/Stop Button==========================
        // ===========================================================

        startButton.setOnClickListener{  // start or stop clock
            startButtonPressed = true

            // 0. Ensure that necessary info exists
            var rate = getRate()
            if (rate == -404.4){  // Something went wrong. Don't continue
                return@setOnClickListener
            }

            // 1. Set up clock file if needed
            var startClock: Boolean
            val totalSeconds = clock.totalSeconds  // Keep the original clock's total seconds, 0 if new clock

            if (!clockStarted){  // If clock hasn't been started
                clock = Clock(Time(LocalDateTime.now()))  // Make a new one with current time
                clock.totalSeconds = totalSeconds
                clock.rate = rate  // Save rate to clock object
                clock.active = true
                createClockFile(clock)  // Save this clock to device
                startClock = true  // We will need to start the clock
                clockStarted = true
                println("[i] Button pressed while clock inactive. Starting clock")

            } else{
                startClock = false  // We will need to stop the clock
                clockStarted = false
                println("[i] Button pressed while clock active. Stopping clock")
            }

            // 2. Start clock or stop clock
            val dbJson: String
            var pay: Double
            var totalSec: Double = 0.0

            if (startClock){  // If we need to start the clock
                notifyView.text = "Clock Started!"

            } else{  // Otherwise if we need to stop the clock

                // 1. Prep clock for db store
                now = clock.update()  // Get current time
                loadDBFile(clock)  // Ensure that the db has been loaded
                println("[i] DB: ${readFile("workDB.json")}")

                // 2. Save clock file
                totalSec = clock.sessionSeconds + clock.totalSeconds
                pay = clock.calculatePay(rate, totalSec)
                clock.totalSeconds = totalSec  // Save total sec to clock before dbEntry fun called
                clock.active = false
                createClockFile(clock)
                println("[i] Clock file: ${readFile("clock.pvcf")}")

                // 3. Output final data and save db
                println("TIME PASSED: ${clock.sessionSeconds}")
                clock.createDBEntry(rate, Time(now))  // This calculates pay and enters it into db
                saveDBFile(clock)  // Save json to a file with the added entry

                notifyView.text = "Clock Stopped!"
                timeWorked.text = "${clock.startTime.getElapsedTimeAsString(now, elapSec = totalSec)}"
                payView.text = "$${String.format("%.02f", pay)} earned"

            }
        }

        // ===========================================================
        // ================Reset Button===============================
        // ===========================================================
        var pressCt = 0  // This will track how many times a user pressed reset in session
        val emptyClock = Clock(Time(LocalDateTime.now()))

        resetButton.setOnClickListener {
            // 0. Determine if clock just started
            // If a new clock or file was created, user may want to purge again
            if (pressCt > 0 && startButtonPressed){
                pressCt = 0  // Reset count
                startButtonPressed = false
            }

            // 1. Try to grab clock file
            var clockFile: MutableList<String> = mutableListOf()
            var newClockFile = ""

            try{
                clockFile = readFile("clock.pvcf").split("\n").toMutableList()
            } catch (t: Throwable){  // This is almost certainly just the file not existing
                println("[X] $t")
                return@setOnClickListener
            }

            // 2. Act according to the number of presses
            if (pressCt < 3){
                notifyView.text = "${3 - pressCt} presses until day data purged"
            }
            else if (pressCt == 3){  // Purge day's data
                println("[i] CF BEFORE PURGE: ${readFile("clock.pvcf")}")
                // 2.1  Assign values to zero and clock to inactive
                clockFile[1] = "0.0"  // Total seconds
                clockFile[2] = false.toString()  // Active state
                clockFile[4] = "0.0"  // Session seconds

                // 2.2 Save new purged clockFile
                for (item in clockFile){
                    newClockFile += "$item\n"
                }
                saveFile(newClockFile, "clock.pvcf")

                // 2.3 Reset clock
                clock = loadClockFile()  // Resets session's clock
                clockStarted = false

                // 2.4 Notify user of purge and clean screen
                println("[i] CF AFTER PURGE: ${readFile("clock.pvcf")}")

                payView.text = ""
                timeWorked.text = ""
                notifyView.text = "Day's data purged"
            }
            else if (pressCt in 4..19){
                notifyView.text = "${20 - pressCt} presses until database wipe"
            }
            else if (pressCt >= 20){  // Purge entire db
                saveDBFile(emptyClock)  // This will save the db of the empty clock, overwriting old one
                notifyView.text = "Database has been wiped"
            }

            pressCt++
        }

        // ===========================================================
        // ================Display Pay Button=========================
        // ===========================================================
        displayPayButton.setOnClickListener {
            var pay: Double
            now = clock.update()  // Update before accessing data required
            val totalSec = clock.sessionSeconds + clock.totalSeconds

            when (clockStarted){
                true -> {
                    // 1. Clear notifications
                    notifyView.text = ""
                    timeWorked.text = ""
                    payView.text = ""

                    // 2. Display pay to screen
                    pay = clock.calculatePay(clock.rate, totalSec)
                    timeWorked.text = "${clock.startTime.getElapsedTimeAsString(now, elapSec = totalSec)}"
                    payView.text = "$${String.format("%.02f", pay)} earned"
                }
                false -> {
                    notifyView.text = "Start clock first to display pay"
                }
            }

        }

        // ===========================================================
        // ================Export Button==============================
        // ===========================================================
        exportDataButton.setOnClickListener {
            var uri: Uri
            val intent = Intent(Intent.ACTION_SEND)


            try{

                // 1. Get uri
                if (File(applicationContext.filesDir, "workDB.json").exists()){
                    uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "com.example.payvis",
                        File(applicationContext.filesDir, "workDB.json")
                    )

                    // 2. Share
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.setType("*/*")
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)

                }

            } catch (t: Throwable){
                notifyView.text = "Unable to export file"
                println("[X] Failed to export file: $t")
            }


        }


    }
}
