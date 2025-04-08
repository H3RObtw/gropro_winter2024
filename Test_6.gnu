reset
set term png size 900,950
set output 'Test_6.png'
set xrange [0:900]
set yrange [0:850]
set size ratio -1

set title "\
// Auftrag Test 6 - Medium set, many squares, medium depth\n\
Benötigte Länge: 85.0cm\n\
Genutzte Fläche: 82.30%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
550 297 650 397 "Square 1" 1
250 297 400 447 "Square 2" 2
0 250 200 450 "Square 3" 3
650 297 750 397 "Square 4" 4
0 0 250 250 "Square 5" 5
400 297 550 447 "Square 6" 6
670 0 880 297 "Rect 1" 7
250 0 670 297 "Rect 2" 8
700 550 800 650 "Square 9" 9
50 450 350 750 "Square 10" 10
0 450 50 500 "Square 11" 11
550 550 700 700 "Square 12" 12
350 550 550 750 "Square 13" 13
350 450 850 550 "Tall 1" 14
50 750 650 850 "Wide 1" 15
EOD

$anchor <<EOD
880 0
200 250
750 297
550 397
650 397
250 447
400 447
850 450
0 500
800 550
700 650
550 700
650 750
50 850
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
