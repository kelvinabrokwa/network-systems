FLAGS = -Xlint:unchecked
all:
	javac $(FLAGS) *.java
clean:
	rm -f *.class
run-server1:
	java -DUDPPORT=8816 -DLOSSRATE=0.50 server1 8817
run-client1:
	java -DUDPPORT=8817 -DLOSSRATE=0.50 client1 box 8818
run-server2:
	java -DUDPPORT=8816 -DLOSSRATE=0.50 server2 8817
run-client2:
	java -DUDPPORT=8817 -DLOSSRATE=0.50 client2 box 8818
