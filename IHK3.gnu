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
0 300 100 400 "Auftrag A" 1
0 400 210 697 "Auftrag B" 2
210 400 630 697 "Auftrag C" 3
0 0 880 300 "Auftrag D" 4
630 400 840 697 "Auftrag E" 5
100 300 200 400 "Auftrag F" 6
420 697 630 994 "Auftrag G" 7
0 697 420 994 "Auftrag H" 8
0 994 880 1294 "Auftrag I" 9
630 697 840 994 "Auftrag J" 10
EOD

$anchor <<EOD
880 0
200 300
840 400
840 697
880 994
0 1294
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
