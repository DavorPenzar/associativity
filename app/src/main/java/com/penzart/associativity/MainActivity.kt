package com.penzart.associativity

import android.content.Context
import android.content.res.AssetManager
import android.os.*
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.penzart.associativity.MainActivity.Companion.GAME_TABLES_DEFAULT_DIRECTORY
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.Integer.min
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.abs

/**
 * Main activity of the application.
 *
 * Difficulty levels labels are:
 * * 1 for "easy",
 * * 2 for "medium",
 * * 3 for "hard",
 * * 0 for "custom" (custom game tables put in external storage).
 * It is not guaranteed that opening other difficulty levels will not throw an exception.  It is
 * also not guaranteed that opening game tables from a root directory other than
 * [GAME_TABLES_DEFAULT_DIRECTORY] will not throw an exception.
 *
 * @property labelOne Label of the first row of cells in the game table in [resources].
 * @property labelTwo Label of the second row of cells in the game table in [resources].
 * @property labelThree Label of the third row of cells in the game table in [resources].
 * @property labelFour Label of the fourth row of cells the game table in [resources].
 *
 * @property labelA Label of the column A of cells in the game table in [resources].
 * @property labelB Label of the column B of cells in the game table in [resources].
 * @property labelC Label of the column C of cells in the game table in [resources].
 * @property labelD Label of the column D of cells in the game table in [resources].
 *
 * @property gameDifficulty Game's difficulty level.
 * @property gameFreshness Game's freshness state (if `true`, the game table should be initialised with new values and all cells closed as when starting the app or starting a new game).
 *
 * @property stopwatchStartness If the stopwatch has started, `true`; `false` otherwise.
 * @property stopwatchStopness If the stopwatch has fully stopped (as when successfully guessing the final solution, giving up or closing the app), `true`; `false` otherwise.
 * @property stopwatchDuration The duration of gameplay measured by the stopwatch in milliseconds.
 * @property stopwatchHandler The [Handler] object of the stopwatch; could be considered the stopwatch itself.
 * @property stopwatchStartTimeStamp The initial timestamp (milliseconds from boot time) for measuring [stopwatchDuration] of gameplay by the stopwatch (it changes when calling methods [onPause] and [onResume] to disregard idle time therefore ultimately it may be a different timestamp so that [stopwatchDuration] would be measured properly and fairly).
 *
 * @property guessOpenness If the guess dialog is open, `true`; `false` otherwise.
 * @property guessGiveUp If giving up is allowed during guessing, `true`; `false` otherwise.
 * @property guessTarget Label of the target of guessing.
 *
 * @property cellsOpenness Mapping from cells' labels to their openness.
 * @property cellsValues Mapping from cells' labels to their values.
 *
 * @property columnsOpenness Mapping from columns' labels to openness of their solutions.
 * @property columnsValues Mapping from columns' labels to their solutions and alternative acceptable answers.
 *
 * @property solutionOpenness Openness of the final solution.
 * @property solutionValue The final solution and alternative acceptable answers.
 *
 */
class MainActivity : AppCompatActivity() {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  COMPANION ELEMENTS                                                                        //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The companion object of the class [MainActivity].
     *
     * @property SIGN_PLUS String of the positive sign—a single plus, i. e. `"+"`.
     * @property SIGN_MINUS String of the negative sign—a single minus, i. e. `"-"`.
     *
     * @property COMMA_DELIMITER String between items separated by a comma—a comma followed by a single whitespace, i. e. `", "`.
     *
     * @property DISPLAY_PARENT_CHILD_TEXT String format for displaying parent and child as `"${parent}: ${child}"`.
     *
     * @property TIME_SPACE_DELIMITER String between days and hours in time's string representation—a single whitespace, i. e. `" "`.
     * @property TIME_COLON_DELIMITER String between hours, minutes and seconds in time's string representation—a colon, i. e. `":"`.
     * @property HOURS_FORMAT String format for hours in time's string representation.
     * @property MINUTES_SECONDS_FORMAT String format for minutes and seconds in time's string representation.
     *
     * @property SUFFIX_OPEN Suffix for labels of cells, columns and solution to save their openness in [onSaveInstanceState] method.
     * @property SUFFIX_VALUE Suffix for labels of cells, columns and solution to save their values in [onSaveInstanceState] method.
     *
     * @property GAME_DIFFICULTY Label to save difficulty level of the game in [onSaveInstanceState] method.
     * @property GAME_FRESHNESS Label to save freshness of the game in [onSaveInstanceState] method.
     *
     * @property STOPWATCH_STARTNESS Label to save whether or not the stopwatch has started in [onSaveInstanceState] method.
     * @property STOPWATCH_STOPNESS Label to save whether or not the stopwatch has stopped in [onSaveInstanceState] method.
     *
     * @property STOPWATCH_PRINT Label to save the printed time of the stopwatch in [onSaveInstanceState] method.
     * @property STOPWATCH_DURATION Label to save the duration of gameplay in [onSaveInstanceState] method.
     *
     * @property CURRENT_TEXT Label to save currently displayed text in [textViewCurrent] in [onSaveInstanceState] method.
     *
     * @property GUESS_OPENNESS Label to save openness of the guess dialog in [onSaveInstanceState] method.
     * @property GUESS_GIVE_UP Label to save the ability to give up in [onSaveInstanceState] method.
     * @property GUESS_TARGET Label to save the target of guessing in [onSaveInstanceState] method.
     * @property GUESS_HINT_ELABORATE Label to save the elaborate hint for guessing in [onSaveInstanceState] method.
     * @property GUESS_HINT_BRIEF Label to save the brief hint of guessing in [onSaveInstanceState] method.
     * @property GUESS_INPUT Label to save the typed guess in [editTextEnterGuess] in [onSaveInstanceState] method.
     *
     * @property GAME_TABLES_DEFAULT_DIRECTORY Relative assets path for the directory with game tables.  **Note: It is not guaranteed that this path ends with a path separator.  To join paths use [Paths.get] method.**
     *
     */
    public companion object {

        ////////////////////////////////////////////////////////////////////////////////////////////
        //  PRIVATE CONSTANTS                                                                     //
        ////////////////////////////////////////////////////////////////////////////////////////////

        private const val SIGN_PLUS: String = "+"
        private const val SIGN_MINUS: String = "-"

        private const val COMMA_DELIMITER: String = ", "

        private const val DISPLAY_PARENT_CHILD_TEXT: String = "%s: %s"

        private const val TIME_SPACE_DELIMITER: String = " "
        private const val TIME_COLON_DELIMITER: String = ":"
        private const val HOURS_FORMAT: String = "%02d"
        private const val MINUTES_SECONDS_FORMAT: String = "%02d%s%04.1f"


        ////////////////////////////////////////////////////////////////////////////////////////////
        //  LABELS FOR [onSaveInstanceState] AND [onRestoreInstanceState] METHODS                 //
        ////////////////////////////////////////////////////////////////////////////////////////////

        private const val SUFFIX_OPEN: String = "Open"
        private const val SUFFIX_VALUE: String = "Value"

        private const val GAME_DIFFICULTY: String = "gameDifficulty"
        private const val GAME_FRESHNESS: String = "isFresh"

        private const val STOPWATCH_STARTNESS: String = "hasStopwatchStarted"
        private const val STOPWATCH_STOPNESS: String = "hasStopwatchStopped"

        private const val STOPWATCH_PRINT: String = "time"
        private const val STOPWATCH_DURATION: String = "duration"

        private const val CURRENT_TEXT: String = "displayCurrent"

        private const val GUESS_OPENNESS: String = "isGuessDialogOpen"
        private const val GUESS_GIVE_UP: String = "guessGiveUp"
        private const val GUESS_TARGET: String = "guessTarget"
        private const val GUESS_HINT_ELABORATE: String = "guessElaborateHint"
        private const val GUESS_HINT_BRIEF: String = "guessBriefHint"
        private const val GUESS_INPUT: String = "guess"


        ////////////////////////////////////////////////////////////////////////////////////////////
        //  PUBLIC CONSTANTS                                                                      //
        ////////////////////////////////////////////////////////////////////////////////////////////

        public const val GAME_TABLES_DEFAULT_DIRECTORY: String = "game_tables"


        ////////////////////////////////////////////////////////////////////////////////////////////
        //  AUXILIARY METHODS                                                                     //
        ////////////////////////////////////////////////////////////////////////////////////////////

        /**
         * Construct a label for saving state in [onSaveInstanceState] from a cell's, column's or the final solution's label and a suffix.
         *
         * @param label An element's label.
         * @param suffix A suffix for construction.
         *
         * @return The constructed mixed label.
         *
         */
        private fun appendSuffix(label: String, suffix: String): String = label + suffix

        /**
         * "Fix" an array of acceptable answers (solutions).
         *
         * If the original array is empty, an array of a single empty string (`arrayOf(String())`)
         * is returned.  Otherwise case-insensitive duplicates are removed (keeping only the first
         * instance in the original array) and all alternative answers are sorted (the subarray from
         * index 1 until the end after the duplicates have been removed), and this newly constructed
         * array is returned.
         *
         * @param acceptables Original array of acceptable answers (solutions).
         *
         * @return A "fixed" array of acceptable answers (solutions).
         *
         * @see isAcceptable
         *
         */
        private fun fixAcceptables(acceptables: Array<String>): Array<String> {
            // If the original array is empty, return an array of a single empty string.
            if (acceptables.isEmpty())
                return arrayOf(String())

            // Construct a new array of fixed acceptable answers.
            val newAcceptables: ArrayList<String> = acceptables.toCollection(ArrayList()).apply {
                // Remove case-insensitive duplicates.
                run {
                    var i: Int = 0
                    var j: Int = 0

                    // Iterate over the array list.
                    while (i < size) {
                        j = i + 1

                        // Remove all case-insensitive duplicates of the `i`-th element.
                        while (j < size) {
                            if (get(i).equals(get(j), true))
                                removeAt(j)
                            else
                                ++j
                        }

                        ++i
                    }
                }

                // Sort alternative answers.
                run {
                    val firstAcceptable: String = removeAt(0)

                    sort()

                    add(0, firstAcceptable)
                }
            }

            // Return the newly constructed "fixed" array.
            return newAcceptables.toArray(arrayOf())
        }

        /**
         * Check if the given guess is an acceptable solution.
         *
         * The [guess] is an acceptable solution if it is case-insensitively equal to at least one
         * acceptable answer in the array of [acceptables].
         *
         * **Note: The method does not automatically equate usual transcriptions (formal or
         * informal) of *special characters*.  For instance, although *ue* is an accepted
         * transcription of *ü* and *c* is an unusual informal transcription of *č*, these
         * characters/strings are considered different when trying to find [guess] among
         * [acceptables].  The reason for this is that there are *a lot* of such characters whose
         * transcriptions may vary among different languages that use them (or maybe not, the
         * original author of this project is not familiar with the world's linguistics), therefore
         * to avoid unfair accidental exclusions of characters and transcription variations this
         * checking was discarded altogether.  To allow different typing variations of answers add
         * them manually to the array of acceptable answers, i. e. [acceptables].**
         *
         * @param guess The given guess.
         * @param acceptables Acceptable answers.
         *
         * @return If [guess] is an acceptable solution, `true`; `false` otherwise.
         *
         * @see fixAcceptables
         *
         */
        private fun isAcceptable(guess: String, acceptables: Array<String>): Boolean {
            // Try to find [guess] among [acceptables], case-insensitively.
            for (acceptable in acceptables)
                if (guess.equals(acceptable, true))
                    return true

            // Return `false` if [guess] was not found.
            return false
        }

        /**
         * Construct the path of a subdirectory of the desired difficulty level in the root directory of game tables.
         *
         * The resulting path is of the same *relativity level* as the given [rootDirectory] path.
         *
         * If [difficultyLevel] is `0`, the resulting path directs to the external storage;
         * otherwise it directs to an assets subdirectory.
         *
         * As 0 is considered a custom difficulty level located on the external storage, if
         * [difficultyLevel] is `0`, the constructed directory is the same as [rootDirectory].
         * Otherwise a subdirectory is chosen according to the value of [difficultyLevel].
         *
         * @param rootDirectory Path of the root directory of game tables.
         * @param difficultyLevel Difficulty level of the desired game table.
         *
         * @return The path of the subdirectory of game tables of the desired difficulty level.
         *
         */
        private fun constructDifficultyLevelSubdirectoryPath(
            rootDirectory: String,
            difficultyLevel: Int
        ): String = when (difficultyLevel) {
            0 -> rootDirectory
            else -> Paths.get(rootDirectory, difficultyLevel.toString()).toString()
        }

        /**
         * Open a random game table with solutions.
         *
         * The method returns a random table read from a file in the appropriate subdirectory of
         * game tables (from the external storage or [assets]).
         *
         * If [difficultyLevel] is `0`, [filesDir] must not be `null` but has to be a valid path to
         * external storage; otherwise [assets] must not be `null` but be a valid [AssetManager].
         *
         * @param rootDirectory Path of the root directory of game tables.
         * @param difficultyLevel Difficulty level of the desired game table.
         *
         * @return [InputStream] of a random game table from the appropriate subdirectory.
         *
         * @see TableReader
         * @see TableReader.readAssociationsTable
         * @see importRandomGameTable
         * @see constructDifficultyLevelSubdirectoryPath
         * @see isGameTablesSubdirectoryNonEmpty
         *
         */
        private fun randomGameTable(
            rootDirectory: String,
            difficultyLevel: Int,
            assets: AssetManager? = null,
            filesDir: File? = null
        ): InputStream = when (difficultyLevel) {
            0 -> {
                // Get the path of the external storage [subdirectory].
                val subdirectory: String = Paths.get(
                    filesDir!!.path,
                    constructDifficultyLevelSubdirectoryPath(
                        rootDirectory,
                        difficultyLevel
                    )
                ).toString()

                // Open external storage [subdirectory].
                val directoryStream: DirectoryStream<Path> = Files.newDirectoryStream(
                    Paths.get(subdirectory)
                )

                // Get the array of paths of items in [directoryStream].
                val items: Array<String> = (
                    ArrayList<String>().apply {
                        for (item in directoryStream)
                            add(item.toString())
                    }
                ).toArray(arrayOf())

                // Close [directoryStream].
                directoryStream.close()

                // Open and return a random item in [items].
                File(items.random()).inputStream()
            }
            else -> {
                // Get the path of the assets [subdirectory].
                val subdirectory: String = constructDifficultyLevelSubdirectoryPath(
                    rootDirectory,
                    difficultyLevel
                )

                // Open and return a random table in the assets [subdirectory].
                assets!!.open(
                    Paths.get(subdirectory, assets.list(subdirectory)!!.random()).toString()
                )
            }
        }

        /**
         * Check if a game tables subdirectory is empty.
         *
         * If [difficultyLevel] is `0`, [filesDir] must not be `null` but has to be a valid path to
         * external storage; otherwise [assets] must not be `null` but be a valid [AssetManager].
         *
         * @param rootDirectory Path of the root directory of game tables.
         * @param difficultyLevel Difficulty level of the desired game table.
         * @param filesDir Main external storage directory.
         * @param assets [AssetManager] of the activity.
         *
         * @return If the sought subdirectory exists and is not empty, `true`; `false` otherwise.
         *
         */
        public fun isGameTablesSubdirectoryNonEmpty(
            rootDirectory: String,
            difficultyLevel: Int,
            assets: AssetManager? = null,
            filesDir: File? = null
        ): Boolean = try {
            when (difficultyLevel) {
                0 -> {
                    // Open external storage directory.
                    val directoryStream: DirectoryStream<Path> = Files.newDirectoryStream(
                        Paths.get(
                            filesDir!!.path,
                            constructDifficultyLevelSubdirectoryPath(
                                rootDirectory,
                                difficultyLevel
                            )
                        )
                    )

                    // Check if [directoryStream] is non-empty.
                    val nonEmpty: Boolean = directoryStream.iterator().hasNext()

                    // Close [directoryStream].
                    directoryStream.close()

                    // Return [nonEmpty].
                    nonEmpty
                }
                else -> {
                    // Return non-emptiness of the [assets] subdirectory.
                    assets!!.list(
                        constructDifficultyLevelSubdirectoryPath(rootDirectory, difficultyLevel)
                    )!!.isNotEmpty()
                }
            }
        } catch (exception: IOException) {
            false
        }

        /**
         * Initialise external storage for defining custom game tables.
         *
         * If [readmeFilename] is `null`, no README file is created.  Otherwise [readmeText] must
         * not be `null` and [readmeText] is written as contents of the README file written in
         * [filesDir] with the filename [readmeFilename].  A new line is appended to [readmeText]
         * even if it already ends with a new line.
         *
         * @param rootDirectory Path of the root directory of game tables.
         * @param filesDir Main external storage directory.
         * @param readmeFilename Filename of the README file to create in [filesDir].
         * @param readmeText Text of the README file.
         *
         */
        public fun initialiseStorage(
            rootDirectory: String,
            filesDir: File,
            readmeFilename: String? = null,
            readmeText: String? = null
        ) {
            // Construct the path of [subdirectory] of custom game tables.
            val subdirectory: String = Paths.get(
                filesDir.path,
                constructDifficultyLevelSubdirectoryPath(rootDirectory, 0)
            ).toString()

            // If [subdirectory] does not exist or exists but is not a directory, create it.
            if (
                if (Files.exists(Paths.get(subdirectory))) {
                    // Open [subdirectory].
                    val file: File = File(subdirectory)

                    // If [file] is not a directory, delete it and set `true`.  Otherwise set
                    // `false`.
                    if (file.isDirectory)
                        false
                    else {
                        // Delete [file].
                        file.delete()

                        // Set `true`.
                        true
                    }
                }
                else
                    true
            )
                File(subdirectory).mkdirs()

            // Open README file to write.
            if (readmeFilename != null) {
                val readmeWriter: BufferedWriter = File(
                    Paths.get(filesDir.path, readmeFilename).toString()
                ).bufferedWriter()

                // Write README file.
                readmeWriter.write(readmeText!!)
                readmeWriter.newLine()

                // Close README file.
                readmeWriter.close()
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  PROPERTIES' DECLARATION AND INITIALISATION                                                //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var labelOne: String = String()
    private var labelTwo: String = String()
    private var labelThree: String = String()
    private var labelFour: String = String()

    private var labelA: String = String()
    private var labelB: String = String()
    private var labelC: String = String()
    private var labelD: String = String()

    private var gameDifficulty: Int = 0

    private var gameFreshness: Boolean = true

    private var stopwatchStartness: Boolean = false
    private var stopwatchStopness: Boolean = false
    private var stopwatchDuration: Long = 0L
    private var stopwatchHandler: Handler = Handler()
    private var stopwatchStartTimeStamp: Long = SystemClock.elapsedRealtime()

    private var guessOpenness: Boolean = false
    private var guessGiveUp: Boolean = false
    private var guessTarget: String = String()

    private val cellsOpenness: HashMap<String, Boolean> = HashMap()
    private val cellsValues: HashMap<String, String> = HashMap()

    private val columnsOpenness: HashMap<String, Boolean> = HashMap()
    private val columnsValues: HashMap<String, Array<String>> = HashMap()

    private var solutionOpenness: Boolean = false
    private var solutionValue: Array<String> = arrayOf()


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  AUXILIARY METHODS                                                                         //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Express time.
     *
     * The time is expressed as a string `"+D days HH:MM:SS.mmm"`, where `+` is the right sign (zero
     * time is given the positive sign), `D` is the number of days, `"days"` is the right plural
     * string of the locale, `HH`, `MM` and `SS` are zero-padded hours, minutes and seconds and
     * `mmm` are milliseconds.  Days and hours may be omitted if they are equal to 0 (if days are
     * included, the hours are also included even if they are equal to 0). If the time is positive,
     * the sign may be omitted.
     *
     * **Note: The current implementation of the function works properly only for left-to-right
     * writing/reading languages whose decimal separator is a single character.**
     *
     * @param milliseconds Time to express given in milliseconds
     * @param includeHours If `true`, hours are expressed even if they are 0.
     * @param includeDays If `true`, days and hours are expressed even if they are 0.
     * @param includeSign If `true`, the sign is expressed even if the time is non-negative.
     *
     */
    private fun expressTime(
        milliseconds: Long,
        includeHours: Boolean = false,
        includeDays: Boolean = false,
        includeSign: Boolean = false
    ): String {
        // Get the absolute time in milliseconds, seconds, minutes, hours and days.
        val absoluteMilliseconds: Long = abs(milliseconds)
        val absoluteSeconds: Long = absoluteMilliseconds / 1000L
        val absoluteMinutes: Long = absoluteSeconds / 60L
        val absoluteHours: Long = absoluteMinutes / 60L
        val absoluteDays = absoluteHours / 24L

        // Initialise the expression as a sign or an empty string.
        var timeExpression = when {
            milliseconds < 0 -> SIGN_MINUS
            includeSign -> SIGN_PLUS
            else -> String()
        }

        // Express the absolute time.

        // Express days.
        if (includeDays || absoluteDays > 0L) {
            val absoluteDaysInt: Int = absoluteDays.toInt()

            timeExpression += (
                    resources.getQuantityString(R.plurals.stopwatch_days, absoluteDaysInt, absoluteDaysInt) +
                            TIME_SPACE_DELIMITER
                    )
        }

        // Express hours.
        if (includeDays || includeHours || absoluteHours > 0L)
            timeExpression += HOURS_FORMAT.format(absoluteHours % 24L) + TIME_COLON_DELIMITER

        // Express minutes, seconds and milliseconds.
        timeExpression += MINUTES_SECONDS_FORMAT.format(
            absoluteMinutes % 60L,
            TIME_COLON_DELIMITER,
            (absoluteMilliseconds % 60000L) / 1000.0
        )

        // Return the expression.
        return timeExpression
    }

    /**
     * Close the input method.
     *
     * @param window Window in which the input method should be closed.
     *
     */
    private fun closeInputMethod(window: IBinder) {
        val input: InputMethodManager? =
            getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        input?.hideSoftInputFromWindow(window, 0)
    }

    /**
     * Get the array of cells' labels.
     *
     * A label of a row or a column may be passed as the argument [rowOrColumn] to get only the
     * labels of cells in a specific row or a column.  If this label is `null`, an array of all
     * cells' labels is sought.  The method does not throw an exception in case of an illegal
     * (non-existing) label but returns an empty array instead.
     *
     * @param rowOrColumn Optional label of a specific row or a column.
     *
     * @return The sought array of cells' labels.
     *
     * @see arrayOfRows
     * @see arrayOfColumns
     *
     */
    private fun arrayOfCells(rowOrColumn: String? = null): Array<String> = when (rowOrColumn) {
        null -> resources.getStringArray(R.array.cells)
        labelOne -> resources.getStringArray(R.array.cells_1)
        labelTwo -> resources.getStringArray(R.array.cells_2)
        labelThree -> resources.getStringArray(R.array.cells_3)
        labelFour -> resources.getStringArray(R.array.cells_4)
        labelA -> resources.getStringArray(R.array.cells_A)
        labelB -> resources.getStringArray(R.array.cells_B)
        labelC -> resources.getStringArray(R.array.cells_C)
        labelD -> resources.getStringArray(R.array.cells_D)
        else -> arrayOf()
    }

    /**
     * Get the array of rows' labels.
     *
     * @return The sought array of rows' labels.
     *
     * @see arrayOfCells
     * @see arrayOfColumns
     *
     */
    private fun arrayOfRows(): Array<String> = resources.getStringArray(R.array.rows)

    /**
     * Get the array of columns' labels.
     *
     * @return The sought array of columns' labels.
     *
     * @see arrayOfCells
     * @see arrayOfRows
     *
     */
    private fun arrayOfColumns(): Array<String> = resources.getStringArray(R.array.columns)

    /**
     * Given a known button in the game table or the button of the final solution, get its label (cell label, column label, final solution label).
     *
     * @param button A game button.
     *
     * @return The label of the element represented by [button].
     *
     */
    private fun retrieveGameElementLabel(button: Button): String = button.tag.toString()

    /**
     * Get the id of the button of a cell in the game table.
     *
     * @param cell Label of the cell.
     *
     * @return Id of the button of [cell].
     *
     * @see idOfColumn
     * @see idOfFinal
     *
     */
    private fun idOfCell(cell: String): Int = resources.getIdentifier(
        resources.getString(R.string.button) + cell,
        resources.getString(R.string.id),
        packageName
    )

    /**
     * Get the id of the button of a column's solution in the game table.
     *
     * @param column Label of the column.
     *
     * @return Id of the button of the [column]'s solution.
     *
     * @see idOfCell
     * @see idOfFinal
     *
     */
    private fun idOfColumn(column: String): Int = resources.getIdentifier(
        resources.getString(R.string.button) + column,
        resources.getString(R.string.id),
        packageName
    )

    /**
     * Get the id of the button of the final solution.
     *
     * @return Id of the button of the final solution.
     *
     * @see idOfCell
     * @see idOfColumn
     *
     */
    private fun idOfFinal(): Int = resources.getIdentifier(
        resources.getString(R.string.button) + resources.getString(R.string.sol),
        resources.getString(R.string.id),
        packageName
    )


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  INITIALISING METHODS                                                                      //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Initialise strings of rows' and columns' names.
     *
     * The rows' and columns' names are read from [R] and saved to properties [labelOne],
     * [labelTwo], [labelThree] and [labelFour], and [labelA], [labelB], [labelC] and [labelD]
     * accordingly.
     *
     */
    private fun initialiseRowsAndColumns() {
        labelOne = resources.getString(R.string.row_1)
        labelTwo = resources.getString(R.string.row_2)
        labelThree = resources.getString(R.string.row_3)
        labelFour = resources.getString(R.string.row_4)

        labelA = resources.getString(R.string.column_A)
        labelB = resources.getString(R.string.column_B)
        labelC = resources.getString(R.string.column_C)
        labelD = resources.getString(R.string.column_D)
    }

    /**
     * Initialise the stopwatch handler.
     *
     */
    private fun initialiseStopwatchHandler() {
        stopwatchHandler = Handler(Looper.getMainLooper())
    }

    /**
     * Reset inner properties to their default values.
     *
     * **Note: Before calling the method, be sure to manually adapt other things.  For instance, if
     * [stopwatch] is set to `true`, be sure that the stopwatch is not running, i. e. call
     * [stopStopwatch] or [resetStopwatch] beforehand.  Also, reset the graphical UI where
     * needed—for instance, if guess dialog is open, it will not be closed after calling this
     * method, although [isOpenGuessDialog] will return `false`.**
     *
     * @param rowsAndColumns If `true`, reset [labelOne], [labelTwo], [labelThree] and [labelFour], and [labelA], [labelB], [labelC] and [labelD].
     * @param gameFreshness If `true`, reset [gameFreshness] (i. e. the game's freshness is reset).
     * @param stopwatch If `true`, reset [stopwatchStartness], [stopwatchStopness], [stopwatchDuration], [stopwatchHandler] and [stopwatchStartTimeStamp] (i. e. the stopwatch is reset, but **not** as with [resetStopwatch] method).
     * @param guessDialog If `true`, reset [guessOpenness] and [guessTarget] (i. e. the guess dialog is reset).
     * @param gameTableAndSolutions If `true`, reset [cellsOpenness], [cellsValues], [columnsOpenness], [columnsValues], [solutionOpenness] and [solutionValue] (i. e. the game table and solutions are reset).
     *
     */
    private fun resetProperties(
        rowsAndColumns: Boolean = false,
        gameFreshness: Boolean = false,
        stopwatch: Boolean = false,
        guessDialog: Boolean = false,
        gameTableAndSolutions: Boolean = false
    ) {
        // Reset rows' and columns' labels if needed.
        if (rowsAndColumns) {
            labelOne = String()
            labelTwo = String()
            labelThree = String()
            labelFour = String()

            labelA = String()
            labelB = String()
            labelC = String()
            labelD = String()
        }

        // Reset the game's freshness if needed.
        if (gameFreshness)
            this.gameFreshness = true

        // Reset the stopwatch if needed.
        if (stopwatch) {
            stopwatchStartness = false
            stopwatchStopness = false
            stopwatchDuration = 0L
            stopwatchHandler = Handler()
            stopwatchStartTimeStamp = SystemClock.elapsedRealtime()
        }

        // Reset the guess dialog if needed.
        if (guessDialog) {
            guessOpenness = false
            guessGiveUp = false
            guessTarget = String()
        }

        // Reset the game table and solutions if needed.
        if (gameTableAndSolutions) {
            cellsOpenness.clear()
            cellsValues.clear()

            columnsOpenness.clear()
            columnsValues.clear()

            solutionOpenness = false
            solutionValue = arrayOf()
        }
    }

    /**
     * Read a random table with solutions.
     *
     * The method reads from
     * `randomGameTable(rootDirectory, difficultyLevel, getExternalFilesDir(null), assets)` using
     * [TableReader.readAssociationsTable] and closes the [InputStream].  The table that was read is
     * then returned.
     *
     * @param rootDirectory The root directory of the game tables sorted in subdirectories by their difficulty level.
     * @param difficultyLevel Difficulty level of the desired game table.
     *
     * @return A random table with solutions.
     *
     * @see TableReader
     * @see TableReader.readAssociationsTable
     * @see constructDifficultyLevelSubdirectoryPath
     * @see isGameTablesSubdirectoryNonEmpty
     * @see randomGameTable
     *
     */
    private fun importRandomGameTable(
        rootDirectory: String = GAME_TABLES_DEFAULT_DIRECTORY,
        difficultyLevel: Int = 0
    ): Bundle {
        // Open a random game table of the chosen difficulty level.
        val inputStream: InputStream = randomGameTable(
            rootDirectory,
            difficultyLevel,
            assets,
            getExternalFilesDir(null)
        )

        // Read the table from [inputStream].
        val table: Bundle = TableReader.readAssociationsTable(inputStream)

        // Close [inputStream].
        inputStream.close()

        // Return [table].
        return table
    }

    /**
     * Initialise the game table.
     *
     * Game freshness is set to `false`, all cells, all columns's solutions and the final solution
     * are set to closed (the corresponding *open* property is set to `false`), and the game table,
     * the columns' solutions and the main solution are set.  **Note: no existing graphical UI is
     * affected by this method, the method only sets what [isGameFresh], [isCellOpen],
     * [cellValue], [isColumnOpen], [columnValue], [isFinalOpen] and [finalValue] methods will
     * return.**
     *
     * @param rootDirectory The root directory of the game tables sorted in subdirectories by their difficulty level.
     * @param difficultyLevel Difficulty level of the desired game table.
     *
     */
    private fun initialiseTable(
        rootDirectory: String = GAME_TABLES_DEFAULT_DIRECTORY,
        difficultyLevel: Int = 0
    ) {
        // Set the game to fresh and reset the game table's and solutions' properties.

        resetProperties(gameFreshness = true, gameTableAndSolutions = true)

        // Set everything to closed (not open).

        for (cell in arrayOfCells())
            changeCellOpennes(cell, false)

        for (column in arrayOfColumns())
            changeColumnOpennes(column, false)

        changeFinalOpennes(false)

        // Import a random game table with solutions.

        val readTable: Bundle = importRandomGameTable(rootDirectory, difficultyLevel)

        // Populate the game table and solutions with the values from [readTable].

        for (cell in arrayOfCells())
            editCellValue(cell, readTable.getString(cell)!!)

        for (column in arrayOfColumns())
            editColumnValue(column, readTable.getStringArray(column)!!)

        editFinalValue(readTable.getStringArray(resources.getString(R.string.sol))!!)

        // Shuffle the table where allowed.

        shuffleTable(
            HashMap<String, Boolean>().apply {
                for (column in arrayOfColumns())
                    put(column, readTable.getBoolean(TableReader.shuffleAllowingLabel(column)))
            },
            readTable.getBoolean(
                TableReader.shuffleAllowingLabel(resources.getString(R.string.sol))
            )
        )

        // Set the game to non-fresh.

        changeGameFreshness(false)
    }

    /**
     * Shuffle the game table.
     *
     * If a column's label is missing in [shuffleColumns], the cells inside the column will not be
     * shuffled (as if the column maps to `fasle`).  Non-existing columns' labels as keys in
     * [shuffleColumns] are ignored.
     *
     * **Note: This method shuffles only the values returned by [cellValue] and [columnValue]
     * methods as the method should be called only when the game table is being initialised,
     * therefore it is assumed that all cells and columns are closed—that the shuffling is
     * irrelevant to the graphical UI.**
     *
     * @param shuffleColumns If a column maps to `true`, its cells are shuffled.
     * @param shuffleFinal If `true`, the columns themselves are shuffled.
     *
     */
    private fun shuffleTable(
        shuffleColumns: Map<String, Boolean> = mapOf(),
        shuffleFinal: Boolean = false
    ) {
        // Shuffle cells inside columns if needed.
        for (column in arrayOfColumns())
            if (shuffleColumns.getOrDefault(column, false)) {
                // Get the original order of cells and the new order.
                val order: ArrayList<String> = arrayOfCells(column).toCollection(ArrayList())
                val reorder: ArrayList<String> = ArrayList(order).apply {
                    shuffle()
                }

                // Generate a new mapping for the column from the previously constructed shuffled
                // [reorder].  The value of [tableValues] at the key of the `i`-th element of the
                // array [reorder] is set as the value of [newTableValuesColumn] at the key of the
                // `i`-th element of the array [order].
                val newTableValuesColumn: HashMap<String, String> =
                    HashMap<String, String>().apply {
                        val n: Int = min(order.size, reorder.size)

                        for (i in 0 until n)
                            put(order[i], cellValue(reorder[i]))
                    }

                // Copy from [newTableValuesColumn] to the original table.
                for (cell in order)
                    editCellValue(cell, newTableValuesColumn[cell]!!)
            }

        // Shuffle columns if needed.
        if (shuffleFinal) {
            // Get the original order of columns and the new order (arrays of labels of cells).
            val order: ArrayList<String> = arrayOfColumns().toCollection(ArrayList())
            val reorder: ArrayList<String> = ArrayList(order).apply {
                shuffle()
            }

            // Generate a new mapping for the game table from the previously constructed shuffled
            // [reorder].  The logic is the same as for shuffling cells inside a column (see above),
            // only this time both [tableValues] and [columnsValues] must be shuffled
            // simultaneously.
            val newTableValues: HashMap<String, String> = HashMap()
            val newColumnsValues: HashMap<String, Array<String>> = HashMap()

            // Number of columns.
            val n: Int = min(order.size, reorder.size)

            // Iterate over arrays [order] and [reorder] and populate [newTableValues] and
            // [newColumnsValues].
            for (i in 0 until n) {
                // Get labels of cells in columns pointed at by the [i]-th element in arrays [order]
                // and [reorder].
                val oldCells: ArrayList<String> = arrayOfCells(order[i]).toCollection(ArrayList())
                val newCells: ArrayList<String> = arrayOfCells(reorder[i]).toCollection(ArrayList())

                // Number of cells in the column.
                val m: Int = min(oldCells.size, newCells.size)

                // Copy from [tableValues] and [columnsValues] to [newTableValues] and
                // [newColumnsValues] but with altered keys.

                for (j in 0 until m)
                    newTableValues[oldCells[j]] = cellValue(newCells[j])

                newColumnsValues[order[i]] = columnValue(reorder[i])
            }

            // Copy from [newTableValues] and [newColumnsValues] to the original table.

            for (cell in arrayOfCells())
                editCellValue(cell, newTableValues[cell]!!)

            for (column in arrayOfColumns())
                editColumnValue(column, newColumnsValues[column]!!)
        }
    }

    /**
     * Make [editTextEnterGuess]' IME action done to be clicking [buttonGuess].
     *
     */
    private fun connectEditTextEnterGuessAndButtonGuess() =
        editTextEnterGuess.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    buttonGuess.performClick()

                    true
                }
                else -> false
            }
        }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  GAME DIFFICULTY AND FRESHNESS                                                             //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Check the game's difficulty level.
     *
     * @return Current difficulty level.
     *
     * @see changeGameDifficulty
     * @see initialiseTable
     * @see onCreate
     *
     */
    private fun whatGameDifficulty(): Int = gameDifficulty

    /**
     * Change the game's difficulty level.
     *
     * **Note: This method merely changes what [whatGameDifficulty] method will return.  To actually
     * play a game of a different difficulty start [MainActivity] with a proper [Intent].
     *
     * @param difficulty New game's difficulty level.
     *
     * @see whatGameDifficulty
     * @see onCreate
     *
     */
    private fun changeGameDifficulty(difficulty: Int) {
        gameDifficulty = difficulty
    }

    /**
     * Check if the game is fresh.
     *
     * @return If the game is fresh, `true`; `false` otherwise.
     *
     */
    private fun isGameFresh(): Boolean = gameFreshness

    /**
     * Set the game's freshness.
     *
     * If [freshness] is `null`, the game's freshness state is toggled (a fresh game will be set to
     * non-fresh and vice versa).
     *
     * **Note: This method merely changes what [isGameFresh] method will return.  To actually
     * refresh a game the graphical UI must be reset and the game table must be initialised among
     * other things—use [prepareNewGame] method to do that.**
     *
     * @param freshness New freshness of the game.
     *
     * @see prepareNewGame
     * @see isGameFresh
     * @see onCreate
     * @see onStart
     * @see onResume
     *
     */
    private fun changeGameFreshness(freshness: Boolean? = null) {
        gameFreshness = freshness ?: !gameFreshness
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  NEW GAME BUTTON                                                                           //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * On-click method for [buttonNewGame].
     *
     * The button finishes [this] activity and brings the user back to the previous activity.  The
     * previous activity should be a [LauncherActivity].
     *
     * @param it The button [buttonNewGame].
     *
     * @see LauncherActivity
     *
     */
    public fun newGame(it: View) = finish()


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  STOPWATCH                                                                                 //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Check if the stopwatch has started.
     *
     * @return If the stopwatch has started, `true`; `false` otherwise.
     *
     * @see hasStopwatchStopped
     * @see startStopwatch
     * @see stopStopwatch
     * @see resetStopwatch
     *
     */
    private fun hasStopwatchStarted(): Boolean = stopwatchStartness

    /**
     * Set the stopwatch's startness.
     *
     * If [startness] is `null`, the stopwatch's startness state is toggled (a started stopwatch
     * will be set to non-started and vice versa).
     *
     * **Note: This method merely changes what [hasStopwatchStarted] method will return.  To
     * actually start the stopwatch call [startStopwatch] method or to reset it call
     * [resetStopwatch] method which will in turn call this method among doing other things.**
     *
     * @param startness New startness of the stopwatch.
     *
     * @see hasStopwatchStarted
     * @see hasStopwatchStopped
     * @see startStopwatch
     * @see stopStopwatch
     * @see resetStopwatch
     *
     */
    private fun changeStopwatchStartness(startness: Boolean? = null) {
        stopwatchStartness = startness ?: !stopwatchStartness
    }

    /**
     * Check if the stopwatch has permanently stopped (until a new game).
     *
     * @return If the stopwatch has permanently stopped, `true`; `false` otherwise.
     *
     * @see hasStopwatchStarted
     * @see startStopwatch
     * @see stopStopwatch
     * @see resetStopwatch
     *
     */
    private fun hasStopwatchStopped(): Boolean = stopwatchStopness

    /**
     * Set the stopwatch's stopness.
     *
     * If [stopness] is `null`, the stopwatch's stopness state is toggled (a stopped stopwatch will
     * be set to non-stopped and vice versa).
     *
     * **Note: This method merely changes what [hasStopwatchStopped] method will return.  To
     * actually stop the stopwatch call [stopStopwatch] method or to reset it call
     * [resetStopwatch] method which will in turn call this method among doing other things.**
     *
     * @param stopness New stopness of the stopwatch.
     *
     * @see hasStopwatchStarted
     * @see hasStopwatchStopped
     * @see startStopwatch
     * @see stopStopwatch
     * @see resetStopwatch
     *
     */
    private fun changeStopwatchStopness(stopness: Boolean? = null) {
        stopwatchStopness = stopness ?: !stopwatchStopness
    }

    /**
     * Get the stopwatch's initial timestamp in milliseconds from boot time.
     *
     * @return The stopwatch's initial timestamp in milliseconds from boot time.
     *
     */
    private fun retrieveStopwatchStartTimestamp(): Long = stopwatchStartTimeStamp

    /**
     * Set the stopwatch's initial timestamp in milliseconds from boot time.
     *
     * @param startTimestamp New stopwatch's initial timestamp in milliseconds from boot time.
     *
     * @see retrieveStopwatchStartTimestamp
     *
     */
    private fun changeStopwatchStartTimestamp(startTimestamp: Long) {
        stopwatchStartTimeStamp = startTimestamp
    }

    /**
     * Get the current stopwatch's duration in milliseconds.
     *
     * @return The current stopwatch's duration in milliseconds.
     *
     */
    private fun retrieveStopwatchDuration(): Long = stopwatchDuration

    /**
     * Set current stopwatch's duration in milliseconds.
     *
     * @param duration New stopwatch's duration in milliseconds.
     *
     * @see retrieveStopwatchDuration
     *
     */
    private fun changeStopwatchDuration(duration: Long) {
        stopwatchDuration = duration
    }

    /**
     * Get the time expressed in [textViewStopwatch].
     *
     * @return The time expressed in [textViewStopwatch] as a [String] object.
     *
     */
    private fun retrieveStopwatchTime(): String = textViewStopwatch.text.toString()

    /**
     * Print time to [textViewStopwatch]
     *
     * @param time Time to print to [textViewStopwatch] as a [String] object.
     *
     * @see retrieveStopwatchTime
     *
     */
    private fun printStopwatchTime(time: String?) {
        textViewStopwatch.text = time
    }

    /**
     * Express the time in milliseconds and print it to [textViewStopwatch].
     *
     * The time is expressed with [expressTime] method and printed with [printStopwatchTime] method.
     *
     * @param time Time to print in milliseconds.
     *
     * @see expressTime
     * @see retrieveStopwatchTime
     *
     */
    private fun displayStopwatchTime(time: Long) =
        printStopwatchTime(expressTime(time))

    /**
     * Start the stopwatch.
     *
     * @see hasStopwatchStarted
     * @see hasStopwatchStopped
     * @see startStopwatch
     * @see resetStopwatch
     *
     */
    private fun startStopwatch() {
        // Reset the stopwatch.
        resetStopwatch(false)

        // Compute the timestamp from which to count time.
        changeStopwatchStartTimestamp(
            SystemClock.elapsedRealtime() - retrieveStopwatchDuration()
        )

        // Set the stopwatch's startness to `true`.
        changeStopwatchStartness(true)

        // Count time and display it in [textViewStopwatch] using [stopwatchHandler].
        stopwatchHandler.post(
            object : Runnable {
                override fun run() {
                    // Count time.
                    changeStopwatchDuration(
                        SystemClock.elapsedRealtime() - retrieveStopwatchStartTimestamp()
                    )

                    // Display time using [displayTime] method.
                    displayStopwatchTime(retrieveStopwatchDuration())

                    // Repeat the process.
                    stopwatchHandler.post(this)
                }
            }
        )
    }

    /**
     * Stop the stopwatch.
     *
     * @param fullStop If `true`, the stopwatch is permanently stopped until starting a new game.
     *
     * @see hasStopwatchStarted
     * @see hasStopwatchStopped
     * @see startStopwatch
     * @see resetStopwatch
     *
     */
    private fun stopStopwatch(fullStop: Boolean = false) {
        // Remove callbacks in [stopwatchHandler].
        stopwatchHandler.removeCallbacksAndMessages(null)

        // If [fullStop], set the stopwatch's startness and stopness to `true`.
        if (fullStop) {
            changeStopwatchStartness(true)
            changeStopwatchStopness(true)
        }
    }

    /**
     * Reset the stopwatch.
     *
     * If stopwatch has started, firstly it is stopped.  Then [textViewStopwatch] is cleared and
     * stopwatch's initial timestamp and, if [resetDuration], duration are reset.  After the method
     * returns, both [hasStopwatchStarted] and [hasStopwatchStopped] methods will return `false`.
     *
     * @param resetDuration If `true`, the stopwatch's duration is reset (set to 0).
     *
     * @see hasStopwatchStarted
     * @see hasStopwatchStopped
     * @see startStopwatch
     * @see stopStopwatch
     *
     */
    private fun resetStopwatch(resetDuration: Boolean = true) {
        // Stop the stopwatch.
        stopStopwatch(true)

        // Reset stopwatch values.
        printStopwatchTime(String())
        changeStopwatchStartTimestamp(SystemClock.elapsedRealtime())
        if (resetDuration)
            changeStopwatchDuration(0L)

        // Reinitialise the stopwatch handler.
        initialiseStopwatchHandler()

        // Set [stopwatchStartness] and [stopwatchStopness] to `false`.
        changeStopwatchStartness(false)
        changeStopwatchStopness(false)
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  GUESS DIALOG                                                                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Find out whether the guess dialog is open or closed.
     *
     * @return `true` if the guess dialog is open, `false` otherwise.
     *
     */
    private fun isOpenGuessDialog(): Boolean = guessOpenness

    /**
     * Set the guess dialog's openness.
     *
     * If [openness] is `null`, the guess dialog's openness state is toggled (an open guess dialog
     * will be set to closed and vice versa).
     *
     * **Note: This method merely changes what [isOpenGuessDialog] method will return.  To
     * actually open the guess dialog call [openGuessDialog] method or to close it call
     * [closeGuessDialog] method which will in turn call this method among doing other things.**
     *
     * @param openness New openness of the guess dialog.
     *
     * @see isOpenGuessDialog
     * @see openGuessDialog
     * @see closeGuessDialog
     *
     */
    private fun changeGuesDialogOpenness(openness: Boolean? = null) {
        guessOpenness = openness ?: !guessOpenness
    }

    /**
     * Open the guess dialog.
     *
     * @see isOpenGuessDialog
     * @see closeGuessDialog
     *
     */
    private fun openGuessDialog() {
        // Set the guess dialog's openness to `true`.
        changeGuesDialogOpenness(true)

        // Actually open the guess dialog.
        scrollViewGuessDialog.visibility = View.VISIBLE
    }

    /**
     * Close the guess dialog.
     *
     * @see isOpenGuessDialog
     * @see closeGuessDialog
     *
     */
    private fun closeGuessDialog() {
        // Close the input method.
        closeInputMethod(editTextEnterGuess.windowToken)

        // Actually close the guess dialog.
        scrollViewGuessDialog.visibility = View.GONE

        // Clear [editTextEnterGuess].
        typeGuess(String())

        // Set the guess dialog's openness to `false`.
        changeGuesDialogOpenness(false)
    }

    /**
     * Check if giving up is allowed during guessing.
     *
     * @return If giving up is allowed during guessing, `true`; `false` otherwise.
     *
     */
    private fun isGuessGivingUpAllowed(): Boolean = guessGiveUp

    /**
     * Change the allowness of giving up during guessing.
     *
     * If [giveUp] is `null`, the guessing's allowness state is toggled (a guessing with allowed
     * giving up will be set to guessing with non-allowed giving up and vice versa).
     *
     * **Note: This method merely changes what [isGuessGivingUpAllowed] method will return.  To
     * actually allow or disallow giving up pass appropriate arguments to [offerColumnGuess] and
     * [offerFinalGuess] methods.**
     *
     * @param giveUp New allowness of giving up during guessing.
     *
     * @see isGuessGivingUpAllowed
     * @see offerColumnGuess
     * @see offerFinalGuess
     *
     */
    private fun changeGuessGivingUpAllowness(giveUp: Boolean? = null) {
        guessGiveUp = giveUp ?: !guessGiveUp
    }

    /**
     * Get the label of the current target of guessing.
     *
     * @return The label of the current target of guessing.
     *
     */
    private fun retrieveGuessTarget(): String = guessTarget

    /**
     * Change the current target of guessing.
     *
     * @param target The label of the new target of guessing.
     *
     * @see retrieveGuessTarget
     *
     */
    private fun changeGuessTarget(target: String) {
        guessTarget = target
    }

    /**
     * Get the current hint for guessing.
     *
     * @param elaborate If `true`, the elaborate hint (i. e. the text in [textViewHint]) is returned; otherwise the brief hint is returned (i. e. `hint` of [editTextEnterGuess]).
     *
     * @return Current hint for guessing.
     *
     */
    private fun retrieveGuessHint(elaborate: Boolean = true): String = if (elaborate)
        textViewHint.text.toString()
    else
        editTextEnterGuess.hint.toString()

    /**
     * Print an elaborate hint in [textViewHint].
     *
     * If [target] is given (if it is not `null`), the hint is displayed as `"target: hint"`, where
     * `target` is [target] and `hint` is [hint].  Otherwise only [hint] is printed.
     *
     * When restoring a previously printed elaborate hint, pass the complete elaborate hint as the
     * argument [hint] while leaving the argument [target] to the default `null` value.
     *
     * @param hint Elaborate hint to print.
     * @param target Target of the solution (column label if the solution is not final).
     *
     * @see retrieveGuessHint
     *
     */
    private fun displayElaborateGuessHint(hint: String?, target: String? = null) {
        textViewHint.text = if (target == null)
            hint
        else
            DISPLAY_PARENT_CHILD_TEXT.format(target, hint)
    }

    /**
     * Print a brief hint in [editTextEnterGuess].
     *
     * @param hint Brief hint to display.
     *
     * @see retrieveGuessHint
     *
     */
    private fun displayBriefGuessHint(hint: String?) {
        editTextEnterGuess.hint = hint
    }

    /**
     * Get the currently written guess in [editTextEnterGuess].
     *
     * @return Currently written guess in [editTextEnterGuess].
     *
     */
    private fun retrieveGuess(): String = editTextEnterGuess.text.toString()

    /**
     * Set the guess in [editTextEnterGuess].
     *
     * @param guess Guess to set in [editTextEnterGuess].
     *
     * @see retrieveGuess
     *
     */
    private fun typeGuess(guess: String?) = editTextEnterGuess.setText(guess)

    /**
     * On-click method for [buttonDismiss].
     *
     * When called, the guess dialog is closed.
     *
     * @param it The [buttonDismiss].
     *
     */
    public fun clickOnDismiss(it: View) = closeGuessDialog()


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  CURRENT TEXT DISPLAY                                                                      //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Get the current text in [textViewCurrent].
     *
     * @return Currently written text in [textViewCurrent].
     *
     */
    private fun retrieveCurrentText(): String = textViewCurrent.text.toString()

    /**
     * Print a text in [textViewCurrent].
     *
     * If [origin] is given (is not `null`), the text is displayed as `"origin: text"`, where
     * `origin` is [origin] and `text` is [text].  Otherwise only [text] is printed.
     *
     * When restoring a previously printed text, pass the complete text as the argument [text] while
     * leaving the argument [origin] to the default `null` value.
     *
     * @param text Text to display.
     * @param origin Origin of the text to display (cell or column label).
     *
     * @see retrieveCurrentText
     *
     */
    private fun displayCurrentText(text: String?, origin: String? = null) {
        textViewCurrent.text = if (origin == null)
            text
        else
            DISPLAY_PARENT_CHILD_TEXT.format(origin, text)
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  GAME TABLE AND BUTTONS                                                                    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Check if a cell is open.
     *
     * @param cell Cell's label.
     *
     * @return If [cell] is open, `true`; `false` otherwise.
     *
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    private fun isCellOpen(cell: String): Boolean = cellsOpenness[cell]!!

    /**
     * Change a cell's openness.
     *
     * If [openness] is `null`, the [cell]'s openness state is toggled (an open [cell] will be set
     * to closed and vice versa).
     *
     * **Note: This method merely changes what [isCellOpen] method will return.  To actually open a
     * cell call [openCell] method which will in turn call this method among doing other things.**
     *
     * @param cell Cell's label.
     * @param openness New openness of [cell].
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    private fun changeCellOpennes(cell: String, openness: Boolean? = null) {
        cellsOpenness[cell] = openness ?: !cellsOpenness[cell]!!
    }

    /**
     * Get a cell's value.
     *
     * @param cell Cell's label.
     *
     * @return The [cell]'s value.
     *
     */
    private fun cellValue(cell: String): String = cellsValues[cell]!!

    /**
     * Set a cell's value.
     *
     * If the old value of [cell] is displayed somewhere, the display will not be updated.
     *
     * @param cell Cell's label.
     * @param value New value of [cell].
     *
     * @see cellValue
     *
     */
    private fun editCellValue(cell: String, value: String) {
        cellsValues[cell] = value
    }

    /**
     * Open a cell in the game table.
     *
     * @param cell Cell's label.
     * @param displayContent If `true`, the content is displayed in [textViewCurrent].
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    private fun openCell(cell: String, displayContent: Boolean = true) {
        // Get [value] of [cell].
        val value: String = cellValue(cell)

        // Get [button] of [cell].
        val button: Button = findViewById(idOfCell(cell))

        // Visually change the appearance of [button], display the value of [cell] in  [button] and
        // set it to open.
        button.background = resources.getDrawable(R.drawable.open_cell_button, theme)
        button.text = value
        changeCellOpennes(cell, true)

        // If [displayContent], display the text in [textViewCurrent].
        if (displayContent)
            displayCurrentText(value, cell)

        // Change [onClick] of [button].
        button.setOnClickListener(this::clickOnOpenCell)

        // Start the stopwatch if it has not started yet.
        if (!(hasStopwatchStarted() || hasStopwatchStopped()))
            startStopwatch()
    }

    /**
     * Check if a column's solution is open.
     *
     * @param column Column's label.
     *
     * @return If the [column]'s solution is open, `true`; `false` otherwise.
     *
     * @see isCellOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    private fun isColumnOpen(column: String): Boolean = columnsOpenness[column]!!

    /**
     * Change a column's solution's openness.
     *
     * If [openness] is `null`, the [column]'s solution's openness state is toggled (an open
     * [column]'s solution will be set to closed and vice versa).
     *
     * This method merely changes what [isColumnOpen] method will return.  To actually open a
     * column's solution call [openColumn] method which will in turn call this method among doing
     * other things.
     *
     * @param column Column's label.
     * @param openness New openness of the [column]'s solution.
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    private fun changeColumnOpennes(column: String, openness: Boolean? = null) {
        columnsOpenness[column] = openness ?: !columnsOpenness[column]!!
    }

    /**
     * Get a column's solution's acceptable answers.
     *
     * @param column Column's label.
     *
     * @return The [column]'s solution's acceptable answers.
     *
     */
    private fun columnValue(column: String): Array<String> = columnsValues[column]!!

    /**
     * Set a column's solution's acceptable answers.
     *
     * If the old acceptable answers of the [column]'s solution are displayed somewhere, the display
     * will not be updated.
     *
     * @param column Column's label.
     * @param value New value of the [column]'s solution's acceptable answers.
     * @param fix If `true`, the raw [value] is not used, instead [fixAcceptables] method is called first; otherwise the raw [value] is used.
     *
     * @see columnValue
     *
     */
    private fun editColumnValue(column: String, value: Array<String>, fix: Boolean = true) {
        columnsValues[column] = if (fix)
            fixAcceptables(value)
        else
            value
    }

    /**
     * Open a column's solution.
     *
     * @param column Column's label.
     * @param recursiveOpen If `true`, all cells in the [column] are opened as well.
     * @param displayContent If `true`, the content is displayed in [textViewCurrent].
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    private fun openColumn(
        column: String,
        recursiveOpen: Boolean = true,
        displayContent: Boolean = true
    ) {
        // Open all cells in [column] first if needed.
        if (recursiveOpen)
            for (cell in arrayOfCells(column))
                openCell(cell, displayContent = false)

        // Get the [column]'s main solution.
        val value: String = columnValue(column)[0]

        // Get [button] of the [column]'s solution.
        val button: Button = findViewById(idOfColumn(column))

        // Visually change the appearance of [button], display the [column]'s main solution in
        // [button] and set it to open.
        button.background = resources.getDrawable(R.drawable.open_column_button, theme)
        button.text = value
        changeColumnOpennes(column, true)

        // Display the solution in [textViewCurrent] if needed.
        if (displayContent)
            displayCurrentText(value, column)

        // Change [onClick] of [button].
        button.setOnClickListener(this::clickOnOpenColumn)
    }

    /**
     * Check if the final solution is open.
     *
     * @return If the final solution is open, `true`; `false` otherwise.
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    private fun isFinalOpen(): Boolean = solutionOpenness

    /**
     * Change the final solution's openness.
     *
     * If [openness] is `null`, the final solution's openness state is toggled (an open final
     * solution will be set to closed and vice versa).
     *
     * This method merely changes what [isFinalOpen] method will return.  To actually open the final
     * solution call [openFinal] method which will in turn call this method among doing other
     * things.
     *
     * @param openness New openness of the final solution.
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    private fun changeFinalOpennes(openness: Boolean? = null) {
        solutionOpenness = openness ?: !solutionOpenness
    }

    /**
     * Get final solution's acceptable answers.
     *
     * @return The final solution's acceptable answers.
     *
     */
    private fun finalValue(): Array<String> = solutionValue

    /**
     * Set final solution's acceptable answers.
     *
     * If the old acceptable answers of the final solution are displayed somewhere, the display will
     * not be updated.
     *
     * @param value New value of the final solution.
     * @param fix If `true`, the raw [value] is not used, instead [fixAcceptables] method is called first; otherwise the raw [value] is used.
     *
     * @see finalValue
     *
     */
    private fun editFinalValue(value: Array<String>, fix: Boolean = true) {
        solutionValue = if (fix)
            fixAcceptables(value)
        else
            value
    }

    /**
     * Open the final solution.
     *
     * The stopwatch is permanently stopped.
     *
     * @param recursiveOpen If `true`, all columns' solutions and cells are opened as well.
     * @param displayContent If `true`, the content is displayed in [textViewCurrent].
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    private fun openFinal(recursiveOpen: Boolean = true, displayContent: Boolean = true) {
        // Permanently stop the stopwatch.
        stopStopwatch(true)

        // Open all columns' solutions and cells first if needed.
        if (recursiveOpen)
            for (column in arrayOfColumns())
                openColumn(column, recursiveOpen = true, displayContent = false)

        // Get the main final solution.
        val value: String = finalValue()[0]

        // Get [button] of the final solution.
        val button: Button = findViewById(idOfFinal())

        // Visually change the appearance of [button], display the main final solution in [button]
        // and set it to open.
        button.background = resources.getDrawable(R.drawable.open_solution_button, theme)
        button.text = value
        changeFinalOpennes(true)

        // Display the solution in [textViewCurrent] if needed.
        if (displayContent)
            displayCurrentText(value)

        // Change [onClick] of [button].
        button.setOnClickListener(this::clickOnOpenFinal)
    }

    /**
     * On-click method for buttons of closed cells in the game table.
     *
     * When called, the cell is opened and the value of the cell is displayed in [textViewCurrent].
     *
     * @param it The button of the cell (instance of [Button] class).
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    public fun clickOnClosedCell(it: View) = openCell(retrieveGameElementLabel(it as Button))

    /**
     * On-click method for buttons of open cells in the game table.
     *
     * When called, the value of the cell is displayed in [textViewCurrent].
     *
     * @param it The button of the cell (instance of [Button] class).
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    public fun clickOnOpenCell(it: View) {
        val cell: String = retrieveGameElementLabel(it as Button)

        displayCurrentText(cellValue(cell), cell)
    }

    /**
     * On-click method for buttons of closed columns' solutions in the game table.
     *
     * When called,
     * 1. if any of the column's cells is open, [offerColumnGuess] method is called
     * 2. otherwise column's label is displayed in [textViewCurrent].
     *
     * If [offerColumnGuess] method is called, the argument `offerGivingUp` will be set to
     * `true` if and only if all cells in the column are opened.
     *
     * @param it The button of the column's solution (instance of [Button] class).
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     * @see offerColumnGuess
     *
     */
    public fun clickOnClosedColumn(it: View) {
        // Get the column's label.
        val column: String = retrieveGameElementLabel(it as Button)

        // Get the array of cells' labels in [column].
        val cells: Array<String> = arrayOfCells(column)

        // Initialise the array of open values in [column].
        val open: Array<String> = Array(cells.size) { String() }

        // Of all the cells in [column], copy only the values of open among them into the array
        // [open].  The variable [n] represents their quantity.

        var n: Int = 0

        for (cell in cells)
            if (isCellOpen(cell))
                open[n++] = cellValue(cell)

        // Act accordingly.
        if (n == 0)
            displayCurrentText(column)
        else
            offerColumnGuess(column, open.copyOf(n), n == open.size)
    }

    /**
     * On-click method for buttons of open columns' solutions in the game table.
     *
     * When called, the column's main solution is displayed in [textViewCurrent].
     *
     * @param it The button of the column's solution (instance of [Button] class).
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    public fun clickOnOpenColumn(it: View) {
        val column: String = retrieveGameElementLabel(it as Button)

        displayCurrentText(columnValue(column)[0], column)
    }

    /**
     * On-click method for the button of closed final solution.
     *
     * When called,
     * 1. if any of the columns' solutions is opened, [offerFinalGuess] method is called
     * 2. otherwise the solution string is displayed in [textViewCurrent].
     *
     * If [offerColumnGuess] method is called, the argument `offerGivingUp` will be set to `true` if
     * and only if all columns are opened.
     *
     * @param it The button of the column's solution (instance of [Button] class).
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     * @see offerFinalGuess
     *
     */
    public fun clickOnClosedFinal(it: View) {
        // Get the array of columns' labels.
        val columns: Array<String> = arrayOfColumns()

        // Initialise the array of open columns' solutions.
        val open: Array<String> = Array(columns.size) { String() }

        // Of all the columns, copy only the open solutions among them into the array [open].  The
        // variable [n] represents their quantity.

        var n: Int = 0

        for (column in columns)
            if (isColumnOpen(column))
                open[n++] = columnValue(column)[0]

        // Act accordingly.
        if (n == 0)
            displayCurrentText(resources.getString(R.string.game_table_solution))
        else
            offerFinalGuess(open.copyOf(n), n == open.size)
    }

    /**
     * On-click method for the button of open final solution.
     *
     * When called, the final solution is displayed in [textViewCurrent].
     *
     * @param it The button of the cell (instance of [Button] class).
     *
     * @see isCellOpen
     * @see isColumnOpen
     * @see isFinalOpen
     * @see openCell
     * @see openColumn
     * @see openFinal
     * @see closeCell
     * @see closeColumn
     * @see closeFinal
     *
     */
    public fun clickOnOpenFinal(it: View) = displayCurrentText(finalValue()[0])


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  METHODS FOR GUESSING SOLUTIONS                                                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Offer the player to guess a column's solution.
     *
     * The guess dialog is opened with correct parameters.
     *
     * @param column Column's label.
     * @param hint Array of values of open cells in [column].
     * @param offerGivingUp If `true`, [buttonGiveUp] is enabled.
     *
     * @see clickOnClosedCell
     * @see clickOnClosedColumn
     * @see clickOnClosedFinal
     * @see offerFinalGuess
     *
     */
    private fun offerColumnGuess(
        column: String,
        hint: Array<String?>?,
        offerGivingUp: Boolean = false
    ) {
        // Set the target of guessing.
        changeGuessTarget(column)

        // Display hints.
        displayElaborateGuessHint(hint!!.joinToString(COMMA_DELIMITER), column)
        displayBriefGuessHint(column)

        // Allow or disallow giving up.
        changeGuessGivingUpAllowness(offerGivingUp)

        // Make [buttonGuess] respond to guessing the [column]'s solution.
        bindButtonGuessToColumn(column)

        // Open the guess dialog.
        openGuessDialog()
    }

    /**
     * Offer the player to guess the final solution.
     *
     * The guess dialog is opened with correct parameters.
     *
     * @param hint Array of solutions of open columns.
     * @param offerGivingUp If `true`, [buttonGiveUp] is enabled.
     *
     * @see clickOnClosedCell
     * @see clickOnClosedColumn
     * @see clickOnClosedFinal
     * @see offerColumnGuess
     *
     */
    private fun offerFinalGuess(hint: Array<String?>?, offerGivingUp: Boolean = false) {
        // Set the target of guessing.
        changeGuessTarget(resources.getString(R.string.sol))

        // Display hints.
        displayElaborateGuessHint(hint!!.joinToString(COMMA_DELIMITER))
        displayBriefGuessHint(resources.getString(R.string.game_table_solution))

        // Allow or disallow giving up.
        changeGuessGivingUpAllowness(offerGivingUp)

        // Make [buttonGuess] respond to guessing the final solution.
        bindButtonGuessToFinal()

        // Open the guess dialog.
        openGuessDialog()
    }

    /**
     * Make [buttonGuess] respond to guessing a column's solution.
     *
     * When clicked, it is checked whether or not the guess typed in [editTextEnterGuess] is correct
     * or not.  If it is correct, [column] and its solution are opened, otherwise the player is
     * informed their guess was wrong by displaying the message in [textViewCurrent].  In the end
     * the guess dialog is closed.
     *
     * Also, clicking [buttonGiveUp] will automatically open [column] and its solution if the button
     * is enabled (if [isGuessGivingUpAllowed] method returns `true`).
     *
     * @param column Column's label.
     *
     * @see clickOnClosedCell
     * @see clickOnClosedColumn
     * @see clickOnClosedFinal
     * @see offerColumnGuess
     * @see isGuessGivingUpAllowed
     *
     */
    private fun bindButtonGuessToColumn(column: String) {
        // Set on-click method of [buttonGuess].
        buttonGuess.setOnClickListener {
            if (guessColumn(column,retrieveGuess())) {
                openColumn(column, displayContent = false)
                displayCurrentText(
                    resources.getString(
                        R.string.guess_dialog_feedback_correct,
                        resources.getString(R.string.guess_dialog_feedback_smiley_face)
                    )
                )
            }
            else
                displayCurrentText(
                    resources.getString(
                        R.string.guess_dialog_feedback_wrong,
                        resources.getString(R.string.guess_dialog_feedback_sad_face)
                    )
                )

            closeGuessDialog()
        }

        // Act accordingly on [buttonGiveUp].
        if (isGuessGivingUpAllowed()) {
            // Enable the button.
            buttonGiveUp.apply {
                isClickable = true
                isEnabled = true
            }

            // Set on-click method.
            buttonGiveUp.setOnClickListener {
                openColumn(column)

                closeGuessDialog()
            }
        }
        else {
            buttonGiveUp.apply {
                // Remove on-click method.
                setOnClickListener(null)

                // Disable the button.
                isEnabled = false
                isClickable = false
            }
        }
    }

    /**
     * Make [buttonGuess] respond to guessing the final solution.
     *
     * When clicked, it is checked whether or not the guess typed in [editTextEnterGuess] is correct
     * or not.  If it is correct, the complete game table along with the final solution are opened,
     * otherwise the player is informed their guess was wrong by displaying the message in
     * [textViewCurrent].  In the end the guess dialog is closed.
     *
     * Also, clicking [buttonGiveUp] will automatically open the complete game table and the final
     * solution if the button is enabled (if [isGuessGivingUpAllowed] method returns `true`).
     *
     * @see clickOnClosedCell
     * @see clickOnClosedColumn
     * @see clickOnClosedFinal
     * @see offerColumnGuess
     * @see isGuessGivingUpAllowed
     *
     */
    private fun bindButtonGuessToFinal() {
        // Set on-click method of [buttonGuess].
        buttonGuess.setOnClickListener {
            if (guessFinal(retrieveGuess())) {
                openFinal(displayContent = false)
                displayCurrentText(
                    resources.getString(
                        R.string.guess_dialog_feedback_correct,
                        resources.getString(R.string.guess_dialog_feedback_smiley_face)
                    )
                )
            }
            else
                displayCurrentText(
                    resources.getString(
                        R.string.guess_dialog_feedback_wrong,
                        resources.getString(R.string.guess_dialog_feedback_sad_face)
                    )
                )

            closeGuessDialog()
        }

        // Act accordingly on [buttonGiveUp].
        if (isGuessGivingUpAllowed()) {
            // Enable the button.
            buttonGiveUp.apply {
                isClickable = true
                isEnabled = true
            }

            // Set on-click method.
            buttonGiveUp.setOnClickListener {
                openFinal()

                closeGuessDialog()
            }
        }
        else {
            buttonGiveUp.apply {
                // Remove on-click method.
                setOnClickListener(null)

                // Disable the button.
                isEnabled = false
                isClickable = false
            }
        }
    }

    /**
     * Offer a guess for a column's solution.
     *
     * The [guess] is considered correct if [isAcceptable] method returns `true` for the second
     * argument being the acceptable answers for the [column]'s solution.
     *
     * @param column Column's label.
     * @param guess An offered guess.
     *
     * @return If the offered [guess] was correct, `true`; `false` otherwise.
     *
     * @see clickOnClosedCell
     * @see clickOnClosedColumn
     * @see clickOnClosedFinal
     * @see fixAcceptables
     * @see isAcceptable
     * @see columnValue
     *
     */
    private fun guessColumn(column: String, guess: String): Boolean =
        isAcceptable(guess, columnValue(column))

    /**
     * Offer a guess for the final solution.
     *
     * The [guess] is considered correct if [isAcceptable] method returns `true` for the second
     * argument being the acceptable answers for the final solution.
     *
     * @param guess An offered guess.
     *
     * @return If the offered [guess] was correct, `true`; `false` otherwise.
     *
     * @see clickOnClosedCell
     * @see clickOnClosedColumn
     * @see clickOnClosedFinal
     * @see fixAcceptables
     * @see isAcceptable
     * @see finalValue
     * @see stopStopwatch
     *
     */
    private fun guessFinal(guess: String): Boolean = isAcceptable(guess, finalValue())


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  ACTIVITY LIFECYCLE METHODS                                                                //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Save all appropriate fragments' state.
     *
     * @param outState [Bundle] in which to place saved state.
     *
     * @see onCreate
     * @see onRestoreInstanceState
     *
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save the game state.
        outState.apply {
            // Save the game's difficulty level.
            putInt(GAME_DIFFICULTY, whatGameDifficulty())

            // Save the game's freshness.
            putBoolean(GAME_FRESHNESS, isGameFresh())

            // Save the state of the stopwatch.
            putBoolean(STOPWATCH_STARTNESS, hasStopwatchStarted())
            putBoolean(STOPWATCH_STOPNESS, hasStopwatchStopped())
            putString(STOPWATCH_PRINT, retrieveStopwatchTime())
            putLong(STOPWATCH_DURATION, retrieveStopwatchDuration())

            // If the game is not fresh, save the state of the game table and the solutions.
            if (!isGameFresh()) {
                // Save the state of the game table.
                for (cell in arrayOfCells()) {
                    putBoolean(appendSuffix(cell, SUFFIX_OPEN), isCellOpen(cell))
                    putString(appendSuffix(cell, SUFFIX_VALUE), cellValue(cell))
                }

                // Save the state of the columns' solutions.
                for (column in arrayOfColumns()) {
                    putBoolean(appendSuffix(column, SUFFIX_OPEN), isColumnOpen(column))
                    putStringArray(appendSuffix(column, SUFFIX_VALUE), columnValue(column))
                }

                // Save the state of the final solution.
                putBoolean(
                    appendSuffix(resources.getString(R.string.sol), SUFFIX_OPEN),
                    isFinalOpen()
                )
                putStringArray(
                    appendSuffix(resources.getString(R.string.sol), SUFFIX_VALUE),
                    finalValue()
                )
            }

            // Save the currently displayed text in [textViewCurrent].
            putString(CURRENT_TEXT, retrieveCurrentText())

            // Save the openness of the guess dialog.
            putBoolean(GUESS_OPENNESS, isOpenGuessDialog())

            // If the guess dialog is open, save its state.
            if (isOpenGuessDialog()) {
                putBoolean(GUESS_GIVE_UP, isGuessGivingUpAllowed())

                putString(GUESS_TARGET, retrieveGuessTarget())

                putString(GUESS_HINT_ELABORATE, retrieveGuessHint(true))
                putString(GUESS_HINT_BRIEF, retrieveGuessHint(false))

                putString(GUESS_INPUT, retrieveGuess())
            }
        }
    }

    /**
     * Restore any view state that had previously been frozen by [onSaveInstanceState].
     *
     * This method is called after onStart when the activity is being re-initialized from a
     * previously saved state, given here in [savedInstanceState].  This method is called between
     * [onStart] and [onPostCreate].
     *
     * @param savedInstanceState The data most recently supplied in [onSaveInstanceState] method.
     *
     * @see onSaveInstanceState
     *
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        // Recover data from [savedInstanceState].
        savedInstanceState.apply {
            // Temporarily set the game as fresh.
            changeGameFreshness(true)

            // Clear properties.
            resetProperties()

            // Set the game's difficulty level.
            changeGameDifficulty(savedInstanceState.getInt(GAME_DIFFICULTY))

            // Recover the state of the stopwatch, but do not restart it yet if needed.
            printStopwatchTime(getString(STOPWATCH_PRINT)!!)
            changeStopwatchStartness(getBoolean(STOPWATCH_STARTNESS))
            changeStopwatchStopness(getBoolean(STOPWATCH_STOPNESS))
            changeStopwatchDuration(getLong(STOPWATCH_DURATION))

            // Recover the freshness of the game, but do not set it yet.
            val restoredGameFreshness: Boolean = getBoolean(GAME_FRESHNESS)

            // If the game has not been fresh, recover the state of the game table and the
            // solutions.
            if (!restoredGameFreshness) {
                // Recover the state of the game table.
                for (cell in arrayOfCells()) {
                    changeCellOpennes(cell, getBoolean(appendSuffix(cell, SUFFIX_OPEN)))
                    editCellValue(cell, getString(appendSuffix(cell, SUFFIX_VALUE))!!)

                    if (isCellOpen(cell))
                        openCell(cell, false)
                }

                // Recover the state of the columns' solutions.
                for (column in arrayOfColumns()) {
                    changeColumnOpennes(column, getBoolean(appendSuffix(column, SUFFIX_OPEN)))
                    editColumnValue(
                        column,
                        getStringArray(appendSuffix(column, SUFFIX_VALUE))!!,
                        false
                    )

                    if (isColumnOpen(column))
                        openColumn(column, recursiveOpen = false, displayContent = false)
                }

                // Recover the state of the final solution.

                changeFinalOpennes(getBoolean(appendSuffix(resources.getString(R.string.sol), SUFFIX_OPEN)))
                editFinalValue(getStringArray(appendSuffix(resources.getString(R.string.sol), SUFFIX_VALUE))!!, false)

                if (isFinalOpen())
                    openFinal(recursiveOpen = false, displayContent = false)
            }

            // Recover the text displayed in [textViewCurrent] and redesplay it.
            displayCurrentText(getString(CURRENT_TEXT)!!)

            // If the guess dialog has been open, recover its state and reopen it.
            if (getBoolean(GUESS_OPENNESS)) {
                changeGuessGivingUpAllowness(getBoolean(GUESS_GIVE_UP))

                changeGuessTarget(getString(GUESS_TARGET)!!)

                val guessTarget: String = retrieveGuessTarget()

                displayElaborateGuessHint(getString(GUESS_HINT_ELABORATE)!!)
                displayBriefGuessHint(getString(GUESS_HINT_BRIEF)!!)

                if (arrayOfColumns().contains(guessTarget))
                    bindButtonGuessToColumn(guessTarget)
                else
                    bindButtonGuessToFinal()

                typeGuess(getString(GUESS_INPUT))

                openGuessDialog()
            }

            // Set the game's freshness to the recovered freshness.
            changeGameFreshness(restoredGameFreshness)
        }
    }

    /**
     * Perform initialisation of all fragments.
     *
     * In case [savedInstanceState] is `null`, the game's difficulty level is read from [intent]
     * (the default value being `1`).  The activity is started from [LauncherActivity] which should
     * provide a proper [intent].
     *
     * Only the game's difficulty level and freshness are recovered from [savedInstanceState] (if
     * not `null`).  All other restoration is done in [onRestoreInstanceState] method.  A fresh game
     * is initialised in [onResume] method.
     *
     * @param savedInstanceState If the activity is being re-initialised after previously being shut down then this [Bundle] contains the data it most recently supplied in [onSaveInstanceState] method.  **Note: Otherwise it is `null`.**
     *
     * @see LauncherActivity
     * @see onSaveInstanceState
     * @see onRestoreInstanceState
     * @see onStart
     * @see onPostCreate
     * @see onResume
     *
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Make [editTextEnterGuess]' IME action done to be clicking [buttonGuess].
        connectEditTextEnterGuessAndButtonGuess()

        // Reset inner properties.
        resetProperties(
            rowsAndColumns = true,
            gameFreshness = true,
            stopwatch = true,
            guessDialog = true,
            gameTableAndSolutions = true
        )

        // Initialise rows' and columns' labels.
        initialiseRowsAndColumns()

        // Initialise the stopwatch handler.
        initialiseStopwatchHandler()

        // If [savedInstanceState] is not `null`, recover the game's freshness; otherwise get the
        // desired difficulty level from [intent].
        if (savedInstanceState != null) {
            changeGameDifficulty(savedInstanceState.getInt(GAME_DIFFICULTY))
            changeGameFreshness(savedInstanceState.getBoolean(GAME_FRESHNESS))
        }
        else
            changeGameDifficulty(intent.getIntExtra(resources.getString(R.string.dif), 0))
    }

    /**
     * Do final initialisation after application code has run.
     *
     * Called when activity start-up is complete (after [onStart] and [onRestoreInstanceState]
     * methods have been called).
     *
     * @param savedInstanceState If the activity is being re-initialised after previously being shut down then this [Bundle] contains the data it most recently supplied in [onSaveInstanceState] method.  **Note: Otherwise it is `null`.**
     *
     */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
    }

    /**
     * Dispatch `onStart()` to all fragments.
     *
     */
    override fun onStart() {
        super.onStart()
    }

    /**
     * Dispatch `onResume()` to fragments, initialise the game if it is fresh and restart the stopwatch if needed.
     *
     * If the game has to bee initialised, the difficulty level is retrieved by calling
     * [whatGameDifficulty] method.
     *
     * @see whatGameDifficulty
     * @see initialiseTable
     * @see hasStopwatchStarted
     * @see hasStopwatchStopped
     * @see startStopwatch
     *
     */
    override fun onResume() {
        super.onResume()

        // If the game is fresh, initialise it.
        if (isGameFresh())
            initialiseTable(difficultyLevel = whatGameDifficulty())

        // If the stopwatch needs to be restarted, restart it.
        if (hasStopwatchStarted() && !hasStopwatchStopped())
            startStopwatch()
    }

    /**
     * Stop the stopwatch if needed and dispatch `onPause()` to fragments.
     *
     * @see hasStopwatchStarted
     * @see hasStopwatchStopped
     * @see stopStopwatch
     *
     */
    override fun onPause() {
        // If the stopwatch needs to be stopped, stop it.
        if (hasStopwatchStarted() && !hasStopwatchStopped())
            stopStopwatch()

        super.onPause()
    }

    /**
     * Dispatch `onStop()` to all fragments.
     *
     */
    override fun onStop() {
        super.onStop()
    }

    /**
     * Destroy all fragments.
     *
     */
    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * Call after [onStop] when the current activity is being re-displayed to the user (the user has navigated back to it).
     *
     * The method will be followed by [onStart] and then [onResume].
     *
     */
    override fun onRestart() {
        super.onRestart()
    }
}
