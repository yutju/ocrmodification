package com.example.myapplication2222;

import android.os.Parcel;
import android.os.Parcelable;

public class Kartrider implements Parcelable {
    private String id; // 상품 ID
    private String name; // 상품 이름
    private int price; // 상품 가격
    private int quantity; // 상품 수량

    // 기본 생성자
    public Kartrider() {}

    // 생성자
    public Kartrider(String id, String name, int price, int quantity) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    // Parcelable 구현을 위한 생성자
    protected Kartrider(Parcel in) {
        id = in.readString();
        name = in.readString();
        price = in.readInt();
        quantity = in.readInt();
    }

    // Parcelable.Creator 구현
    public static final Creator<Kartrider> CREATOR = new Creator<Kartrider>() {
        @Override
        public Kartrider createFromParcel(Parcel in) {
            return new Kartrider(in);
        }

        @Override
        public Kartrider[] newArray(int size) {
            return new Kartrider[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeInt(price);
        dest.writeInt(quantity);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // Getter 및 Setter 메서드
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

