reset
set term png size 900,1002
set output 'Test_6.png'
set xrange [0:900]
set yrange [0:902]
set size ratio -1

set title "\
// Auftrag Test 6 - Medium set, many squares, medium depth\n\
Benötigte Länge: 82.0cm\n\
Genutzte Fläche: 85.31%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
200 0 300 100 "Square 1" 1
300 0 450 150 "Square 2" 2
0 0 200 200 "Square 3" 3
200 100 300 200 "Square 4" 4
300 150 550 400 "Square 5" 5
450 0 600 150 "Square 6" 6
0 200 297 410 "Rect 1" 7
600 0 897 420 "Rect 2" 8
300 420 400 520 "Square 9" 9
0 420 300 720 "Square 10" 10
300 520 350 570 "Square 11" 11
350 520 500 670 "Square 12" 12
500 520 700 720 "Square 13" 13
400 420 900 520 "Tall 1" 14
0 720 600 820 "Wide 1" 15
EOD

$anchor <<EOD
897 0
550 150
297 200
300 400
0 410
900 420
700 520
300 570
350 670
600 720
0 820
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
