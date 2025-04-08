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
0 210 297 420 "1" 1
0 420 297 630 "2" 2
297 420 594 630 "3" 3
0 0 297 210 "4" 4
297 0 594 420 "5" 5
594 0 891 420 "6" 6
594 420 742 630 "7" 7
742 420 890 630 "8" 8
420 1050 568 1260 "9" 9
716 1050 821 1198 "10" 10
716 1198 821 1346 "11" 11
0 630 594 1050 "12" 12
0 1050 210 1347 "13" 13
594 630 891 1050 "14" 14
568 1050 716 1260 "15" 15
210 1050 420 1347 "16" 16
780 1347 880 1447 "17" 17
630 1347 780 1497 "18" 18
420 1347 630 1644 "19" 19
0 1347 420 1644 "20" 20
EOD

$anchor <<EOD
891 0
890 420
891 630
821 1050
821 1198
420 1260
568 1260
716 1346
880 1347
780 1447
630 1497
0 1644
420 1644
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
