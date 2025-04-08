reset
set term png size 900,1744
set output 'Test_13.png'
set xrange [0:900]
set yrange [0:1644]
set size ratio -1

set title "\
// Auftrag Test 13 - Dominance of standard A sizes, med depth\n\
Benötigte Länge: 164.4cm\n\
Genutzte Fläche: 92.79%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
0 0 297 210 "A4 P1" 1
297 0 594 210 "A4 P2" 2
594 0 891 210 "A4 P3" 3
0 210 297 420 "A4 P4" 4
297 210 594 630 "A3 P1" 5
594 210 891 630 "A3 P2" 6
0 420 148 630 "A5 P1" 7
148 420 296 630 "A5 P2" 8
0 630 148 840 "A5 P3" 9
148 630 253 778 "A6 P1" 10
148 778 253 926 "A6 P2" 11
253 630 847 1050 "A2 P1" 12
253 1050 463 1347 "A4 P5" 13
463 1050 883 1347 "A3 P3" 14
0 840 148 1050 "A5 P4" 15
0 1050 210 1347 "A4 P6" 16
0 1347 100 1447 "SQR 1" 17
100 1347 250 1497 "SQR 2" 18
250 1347 460 1644 "A4 P7" 19
460 1347 880 1644 "A3 P4" 20
EOD

$anchor <<EOD
891 0
891 210
296 420
847 630
148 926
210 1050
883 1050
880 1347
0 1447
100 1497
250 1644
460 1644
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
