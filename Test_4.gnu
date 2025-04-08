reset
set term png size 900,460
set output 'Test_4.png'
set xrange [0:900]
set yrange [0:360]
set size ratio -1

set title "\
// Auftrag Test 4 - Small set, low depth\n\
Benötigte Länge: 36.0cm\n\
Genutzte Fläche: 84.84%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
0 210 150 360 "Part A" 1
297 0 597 200 "Part B" 2
150 210 550 310 "Part C" 3
0 0 297 210 "Part D" 4
597 0 897 300 "Part E" 5
EOD

$anchor <<EOD
897 0
297 200
550 210
597 300
150 310
0 360
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
