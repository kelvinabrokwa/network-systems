FLAGS = -Xlint:unchecked
all:
	javac $(FLAGS) *.java
clean:
	rm -f *.class
run-server1:
	java -DUDPPORT=8816 server1 8817
run-client1:
	java -DUDPPORT=8817 client1 192.168.0.5 8817
run-server2:
	java -DUDPPORT=8816 -DLOSSRATE=0.50 server2 8817
run-client2:
	java -DUDPPORT=8817 -DLOSSRATE=0.50 client2 192.168.0.5 8817
