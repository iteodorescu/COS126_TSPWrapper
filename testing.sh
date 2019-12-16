
javac-introcs *.java

for file in ./test_files/round1/*; do
    echo "FILE NAME: " $file >&2
    echo "nearest time" >&2
    time java-introcs TSPMap n < $file
    echo "smallest time" >&2
    time java-introcs TSPMap s < $file
    echo "*********" >&2
    echo '' >&2
done

