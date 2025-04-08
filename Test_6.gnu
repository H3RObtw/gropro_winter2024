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
300 297 400 397 "Square 1" 1
0 297 150 447 "Square 2" 2
420 250 620 450 "Square 3" 3
670 297 770 397 "Square 4" 4
420 0 670 250 "Square 5" 5
150 297 300 447 "Square 6" 6
670 0 880 297 "Rect 1" 7
0 0 420 297 "Rect 2" 8
500 550 600 650 "Square 9" 9
600 450 900 750 "Square 10" 10
350 650 400 700 "Square 11" 11
200 650 350 800 "Square 12" 12
0 650 200 850 "Square 13" 13
0 550 500 650 "Tall 1" 14
0 450 600 550 "Wide 1" 15
EOD

$anchor <<EOD
880 0
620 250
400 297
770 297
300 397
670 397
0 447
150 447
400 650
500 650
350 700
600 750
200 800
0 850
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
