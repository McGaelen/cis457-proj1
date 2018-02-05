tc qdisc add dev s1-eth1 root netem loss 5% delay 5ms reorder 5% duplicate 5%
tc qdisc add dev s1-eth1 root netem less 5% delay 5ms reorder 5% duplicate 5%