int func(int t) {
   
   int sum = 0;
     int a = 0;
     while(a < 100){
        sum = sum +a;
        ++a;
     }
   
   return sum;
}

void main () {
   int t = 100;  
   _print(func(t));
   
   return;
}