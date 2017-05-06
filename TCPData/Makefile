FLAGS = -Xlint:unchecked
all:
	javac $(FLAGS) *.java
clean:
	rm -f *.class
run-server3:
	java -DUDPPORT=8816 -DLOSSRATE=0 server3 8817
run-client3:
	java -DUDPPORT=8816 -DLOSSRATE=0 client3 bg2 8817

