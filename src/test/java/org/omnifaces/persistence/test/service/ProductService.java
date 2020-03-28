/*
 * Copyright 2020 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.test.service;

import java.util.List;

import javax.ejb.Stateless;

import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.Product;

@Stateless
public class ProductService extends BaseEntityService<Long, Product> {

	public Product getByIdWithUserRoles(Long id) {
		return getEntityManager()
			.createQuery("SELECT p FROM Product p JOIN FETCH p.userRoles WHERE p.id = :id", Product.class)
			.setParameter("id", id)
			.getSingleResult();
	}

	public List<Product> getAllWithUserRoles() {
		return getEntityManager()
			.createQuery("SELECT p FROM Product p JOIN FETCH p.userRoles", Product.class)
			.getResultList();
	}

	public int getRawProductStatus(Long id) {
		return (int) getEntityManager()
			.createNativeQuery("SELECT p.productStatus FROM product p WHERE p.id = :id").setParameter("id", id)
			.getSingleResult();
	}

	@SuppressWarnings("unchecked")
	public List<String> getRawUserRoles(Long id) {
		return getEntityManager()
			.createNativeQuery("SELECT pu.userRoles FROM product p INNER JOIN product_userRoles pu on pu.product_id = p.id WHERE p.id = :id")
			.setParameter("id", id)
			.getResultList();
	}

}