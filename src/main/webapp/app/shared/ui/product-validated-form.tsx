import React, { useEffect } from 'react';
import { Form } from 'reactstrap';
import { ValidatedField } from 'react-jhipster';
import { FieldError, FormProvider, useForm, useFormContext } from 'react-hook-form';

type ProductValidatedFormProps = {
  defaultValues: Record<string, unknown>;
  onSubmit: (values: Record<string, unknown>) => void;
  children: React.ReactNode;
  formKey?: string | number;
};

/**
 * `ValidatedForm` only wires direct field children. Product forms use nested visual sections, so this small adapter
 * keeps the existing react-jhipster validation behavior while allowing fields to live inside those sections.
 */
export const ProductValidatedForm = ({ defaultValues, onSubmit, children, formKey }: ProductValidatedFormProps) => {
  const methods = useForm({ mode: 'onTouched', defaultValues });

  useEffect(() => {
    methods.reset(defaultValues);
  }, [defaultValues, methods]);

  return (
    <FormProvider {...methods} key={formKey}>
      <Form onSubmit={methods.handleSubmit(onSubmit)}>{children}</Form>
    </FormProvider>
  );
};

type ProductValidatedFieldProps = React.ComponentProps<typeof ValidatedField>;

export const ProductValidatedField = ({ name, ...props }: ProductValidatedFieldProps) => {
  const {
    register,
    formState: { errors, touchedFields, dirtyFields },
  } = useFormContext();

  return (
    <ValidatedField
      {...props}
      name={name}
      register={register}
      error={name ? (errors[name] as FieldError | undefined) : undefined}
      isTouched={name ? touchedFields[name] : undefined}
      isDirty={name ? dirtyFields[name] : undefined}
    />
  );
};
