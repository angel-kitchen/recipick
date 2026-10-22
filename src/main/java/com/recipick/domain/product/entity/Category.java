package com.recipick.domain.product.entity;

import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, unique = true)
    private String name;

    private boolean deleted;

    @Builder
    private Category(String name){
        this.name = name;
    }

    public void rename(String newName){
        validateNotDeleted();
        this.name = newName;
    }

    public void delete(){
        validateNotDeleted();
        this.deleted = true;
    }

    private void validateNotDeleted(){
        if(deleted){
            throw new BusinessException(ProductErrorCode.CATEGORY_ALREADY_DELETED);
        }
    }

    public void restore(){
        this.deleted = false;
    }
}
