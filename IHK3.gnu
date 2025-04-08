reset
set term png size 900,1394
set output 'IHK3.png'
set xrange [0:900]
set yrange [0:1294]
set size ratio -1

set title "\
// Auftrag IHK3\n\
Benötigte Länge: 129.4cm\n\
Genutzte Fläche: 89.90%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
0 0 100 100 "Auftrag A" 1
0 100 210 397 "Auftrag B" 2
210 100 630 397 "Auftrag C" 3
0 397 880 697 "Auftrag D" 4
630 100 840 397 "Auftrag E" 5
100 0 200 100 "Auftrag F" 6
0 697 210 994 "Auftrag G" 7
210 697 630 994 "Auftrag H" 8
0 994 880 1294 "Auftrag I" 9
630 697 840 994 "Auftrag J" 10
EOD

$anchor <<EOD
200 0
840 100
880 397
840 697
880 994
0 1294
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
