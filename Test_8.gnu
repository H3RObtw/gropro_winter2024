reset
set term png size 900,1717
set output 'Test_8.png'
set xrange [0:900]
set yrange [0:1617]
set size ratio -1

set title "\
// Auftrag Test 8 - Large set (25), lower depth for speed\n\
Benötigte Länge: 147.0cm\n\
Genutzte Fläche: 82.66%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
0 150 100 250 "Ord A" 1
0 0 200 150 "Ord B" 2
200 0 350 200 "Ord C" 3
350 0 650 100 "Ord D" 4
350 100 650 200 "Ord E" 5
650 0 900 250 "Ord F" 6
0 250 400 300 "Ord G" 7
400 250 800 300 "Ord H" 8
0 300 297 510 "Ord I" 9
297 300 594 510 "Ord J" 10
594 300 714 480 "Ord K" 11
714 300 894 420 "Ord L" 12
0 510 330 620 "Ord M" 13
330 510 440 840 "Ord N" 14
0 620 220 840 "Ord O" 15
220 620 300 700 "Ord P" 16
440 510 890 660 "Ord Q" 17
440 660 890 810 "Ord R" 18
0 840 600 940 "Ord S" 19
0 940 600 1040 "Ord T" 20
600 840 870 1110 "Ord U" 21
0 1040 190 1350 "Ord V" 22
190 1040 500 1230 "Ord W" 23
190 1230 690 1350 "Ord X" 24
0 1350 500 1470 "Ord Y" 25
EOD

$anchor <<EOD
900 0
100 150
200 200
350 200
800 250
894 300
714 420
594 480
890 510
300 620
890 660
220 700
440 810
870 840
500 1040
600 1110
690 1230
500 1350
0 1470
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
