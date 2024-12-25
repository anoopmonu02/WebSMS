package com.smsweb.sms.services.universal;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.universal.Category;
import com.smsweb.sms.repositories.universal.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Optional;
@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    @Autowired
    public CategoryService(CategoryRepository categoryRepository){
        this.categoryRepository = categoryRepository;
    }

    public List<Category> getAllCategories(){return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));}
    //Sort.by(Sort.Direction.ASC, "id")
    public Category saveCategory(Category category) {
        try{
            return categoryRepository.save(category);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Category already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save category: "+e.getLocalizedMessage());
        }
    }

    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

}
