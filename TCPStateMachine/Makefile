FLAGS = -Xlint:unchecked
all:
	javac $(FLAGS) *.java
clean:
	rm -f *.class
run-server1:
	java -DUDPPORT=8816 server1 8817
run-client1:
	java -DUDPPORT=8816 client1 bg1 8817
run-server2:
	java -DUDPPORT=8816 -DLOSSRATE=0.75 server2 8817
run-client2:
	java -DUDPPORT=8816 -DLOSSRATE=0.75 client2 bg1 8817
