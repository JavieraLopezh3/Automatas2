{
public class Impuestos{
	float [] montos;

	public Impuestos(){
		montos = new float[];
	}

	public float[] AgregarImpuesto(float monto){
		montos = new float[];
		float calculoImpuesto = monto * .16;
		if(!montos.exists(calculoImpuesto)){
			montos[] = montos * 16;
		}
	}

	public void ImprimirImpuestos(){
		for(int i = 0; i < monto; i++){
			print(monto[i]);
		}
	}
	}
}

