package com.teletracker.service.controllers.annotations;

import com.teletracker.service.controllers.validation.ItemReleaseYearValidator;
import com.twitter.finatra.validation.Validation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ PARAMETER })
@Retention(RUNTIME)
@Validation(validatedBy = ItemReleaseYearValidator.class)
public @interface ItemReleaseYear { }
