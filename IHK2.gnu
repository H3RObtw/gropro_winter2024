reset
set term png size 900,1068
set output 'IHK2.png'
set xrange [0:900]
set yrange [0:968]
set size ratio -1

set title "\
// Auftrag IHK2\n\
Benötigte Länge: 88.0cm\n\
Genutzte Fläche: 92.98%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
0 210 100 310 "Auftrag A" 1
0 0 297 210 "Auftrag B" 2
297 0 594 420 "Auftrag C" 3
594 0 894 880 "Auftrag D" 4
0 310 297 520 "Auftrag E" 5
297 420 594 840 "Auftrag H" 8
0 520 297 817 "Auftrag J" 10
EOD

$anchor <<EOD
894 0
100 210
0 817
297 840
594 880
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
