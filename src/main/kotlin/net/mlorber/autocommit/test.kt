package net.mlorber.autocommit

fun main() {

    // Main constants and functions
    val RC = "\u001b[0m" // Reset foreground and background colors. --> $RC
    val R = "\u001b[7m" // Invert foreground to background. --> $R
    val U = "\u001b[4m" // Underline. --> $U
    val B = "\u001b[1m" // Bold. --> $B
    val I = "\u001b[3m" // Italic. --> $I
    val S = "\u001b[9m" // Strikethrough the text. --> $S

    // 256 Colors
    fun fg(n: Int) =
        "\u001b[38;5;${n}m" // Set a foreground color. --> ${fg(40)} //Sets a green color.
    fun bg(n: Int) =
        "\u001b[48;5;${n}m" // Set a background color. --> ${bg(196)} //Sets a red color.

    // RGB Colors
    fun rgbfg(r: Int, g: Int, b: Int) =
        "\u001b[38;2;$r;$g;${b}m" // Set a RGB foreground color. --> ${rgbfg(0,255,0)} //Sets a
                                  // green color.
    fun rgbbg(r: Int, g: Int, b: Int) =
        "\u001b[48;2;$r;$g;${b}m" // Set a RGB background color. --> ${rgbbg(255,0,0)} //Sets a red
                                  // color.

    // Optional function
    fun colorTest() { // When called shows all 256 colors and they respectively numbers. -->
                      // colorTest()
        for (i in 0..9) {
            print("\u001b[38;5;${i}m■$i   ")
        }
        for (i in 10..19) {
            print("\u001b[38;5;${i}m■$i  ")
        }
        println("")
        for (i in 20..39) {
            print("\u001b[38;5;${i}m■$i  ")
        }
        println("")
        for (i in 40..59) {
            print("\u001b[38;5;${i}m■$i  ")
        }
        println("")
        for (i in 60..79) {
            print("\u001b[38;5;${i}m■$i  ")
        }
        println("")
        for (i in 80..99) {
            print("\u001b[38;5;${i}m■$i  ")
        }
        println("")
        for (i in 100..119) {
            print("\u001b[38;5;${i}m■$i ")
        }
        println("")
        for (i in 120..139) {
            print("\u001b[38;5;${i}m■$i ")
        }
        println("")
        for (i in 140..159) {
            print("\u001b[38;5;${i}m■$i ")
        }
        println("")
        for (i in 160..179) {
            print("\u001b[38;5;${i}m■$i ")
        }
        println("")
        for (i in 180..199) {
            print("\u001b[38;5;${i}m■$i ")
        }
        println("")
        for (i in 200..219) {
            print("\u001b[38;5;${i}m■$i ")
        }
        println("")
        for (i in 220..239) {
            print("\u001b[38;5;${i}m■$i ")
        }
        println("")
        for (i in 240..255) {
            print("\u001b[38;5;${i}m■$i ")
        }
    }

    colorTest()
    println("")
    print("\u001b[38;5;25m■  ")
    println("")
}
