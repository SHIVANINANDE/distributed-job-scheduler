import React, { useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  Box,
  Typography,
  Slider,
  FormHelperText,
  Autocomplete,
  Stack,
  Alert,
} from '@mui/material';
import { Add, Close } from '@mui/icons-material';
import { useJobs, useCreateJob } from '../../hooks/useApiQueries';

const jobSchema = yup.object({
  name: yup.string().required('Job name is required').min(3, 'Name must be at least 3 characters'),
  description: yup.string().optional(),
  jobType: yup.string().required('Job type is required'),
  priority: yup.number().min(1).max(10).required('Priority is required'),
  estimatedDuration: yup.number().min(1, 'Duration must be positive').optional(),
  maxRetries: yup.number().min(0).max(10).required('Max retries is required'),
  dependencies: yup.array().of(yup.string()).optional(),
  tags: yup.array().of(yup.string()).optional(),
}).required();

interface JobFormData {
  name: string;
  description?: string;
  jobType: string;
  priority: number;
  estimatedDuration?: number;
  maxRetries: number;
  dependencies?: string[];
  tags?: string[];
}

interface JobCreationFormProps {
  open: boolean;
  onClose: () => void;
}

const jobTypes = [
  'DATA_PROCESSING',
  'COMPUTE_INTENSIVE',
  'BATCH_PROCESSING',
  'ETL',
  'MACHINE_LEARNING',
  'REPORT_GENERATION',
  'IMAGE_PROCESSING',
  'VIDEO_ENCODING',
  'DATABASE_BACKUP',
  'SYSTEM_MAINTENANCE',
];

const commonTags = [
  'production',
  'development',
  'urgent',
  'batch',
  'real-time',
  'high-memory',
  'cpu-intensive',
  'io-intensive',
  'scheduled',
  'manual',
];

const JobCreationForm: React.FC<JobCreationFormProps> = ({ open, onClose }) => {
  const [tagInput, setTagInput] = useState('');
  const { data: existingJobs = [] } = useJobs();
  const createJobMutation = useCreateJob();

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    watch,
    setValue,
  } = useForm<JobFormData>({
    resolver: yupResolver(jobSchema) as any,
    defaultValues: {
      name: '',
      description: '',
      jobType: '',
      priority: 5,
      estimatedDuration: 60,
      maxRetries: 3,
      dependencies: [],
      tags: [],
    },
  });

  const watchedTags = watch('tags') || [];
  const watchedDependencies = watch('dependencies') || [];

  const handleClose = () => {
    reset();
    setTagInput('');
    onClose();
  };

  const onSubmit = async (data: JobFormData) => {
    try {
      await createJobMutation.mutateAsync({
        ...data,
        status: 'PENDING',
        retryCount: 0,
        createdAt: new Date().toISOString(),
      });
      handleClose();
    } catch (error) {
      console.error('Failed to create job:', error);
    }
  };

  const handleAddTag = (tag: string) => {
    if (tag && !watchedTags.includes(tag)) {
      setValue('tags', [...watchedTags, tag]);
    }
    setTagInput('');
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setValue('tags', watchedTags.filter(tag => tag !== tagToRemove));
  };

  const availableJobs = existingJobs.filter(job => 
    job.status === 'COMPLETED' || job.status === 'PENDING'
  );

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Typography variant="h6">Create New Job</Typography>
          <Button onClick={handleClose} size="small" color="inherit">
            <Close />
          </Button>
        </Box>
      </DialogTitle>

      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogContent>
          <Stack spacing={3}>
            {createJobMutation.error && (
              <Alert severity="error">
                Failed to create job. Please try again.
              </Alert>
            )}

            {/* Basic Information */}
            <Box>
              <Typography variant="h6" gutterBottom>
                Basic Information
              </Typography>
              <Stack spacing={2}>
                <Controller
                  name="name"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      label="Job Name"
                      fullWidth
                      error={!!errors.name}
                      helperText={errors.name?.message}
                      required
                    />
                  )}
                />

                <Controller
                  name="description"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      label="Description"
                      fullWidth
                      multiline
                      rows={3}
                      error={!!errors.description}
                      helperText={errors.description?.message}
                    />
                  )}
                />

                <Controller
                  name="jobType"
                  control={control}
                  render={({ field }) => (
                    <FormControl fullWidth error={!!errors.jobType} required>
                      <InputLabel>Job Type</InputLabel>
                      <Select {...field} label="Job Type">
                        {jobTypes.map((type) => (
                          <MenuItem key={type} value={type}>
                            {type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase())}
                          </MenuItem>
                        ))}
                      </Select>
                      {errors.jobType && (
                        <FormHelperText>{errors.jobType.message}</FormHelperText>
                      )}
                    </FormControl>
                  )}
                />
              </Stack>
            </Box>

            {/* Configuration */}
            <Box>
              <Typography variant="h6" gutterBottom>
                Configuration
              </Typography>
              <Stack spacing={3}>
                <Box>
                  <Typography gutterBottom>
                    Priority: {watch('priority')}
                  </Typography>
                  <Controller
                    name="priority"
                    control={control}
                    render={({ field }) => (
                      <Slider
                        {...field}
                        min={1}
                        max={10}
                        step={1}
                        marks={[
                          { value: 1, label: 'Low' },
                          { value: 5, label: 'Medium' },
                          { value: 10, label: 'High' },
                        ]}
                        valueLabelDisplay="auto"
                      />
                    )}
                  />
                </Box>

                <Controller
                  name="estimatedDuration"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      label="Estimated Duration (seconds)"
                      type="number"
                      fullWidth
                      error={!!errors.estimatedDuration}
                      helperText={errors.estimatedDuration?.message}
                    />
                  )}
                />

                <Controller
                  name="maxRetries"
                  control={control}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      label="Max Retries"
                      type="number"
                      fullWidth
                      error={!!errors.maxRetries}
                      helperText={errors.maxRetries?.message}
                      required
                    />
                  )}
                />
              </Stack>
            </Box>

            {/* Dependencies */}
            <Box>
              <Typography variant="h6" gutterBottom>
                Dependencies
              </Typography>
              <Controller
                name="dependencies"
                control={control}
                render={({ field }) => (
                  <Autocomplete
                    {...field}
                    multiple
                    options={availableJobs.map(job => job.id)}
                    getOptionLabel={(option) => {
                      const job = availableJobs.find(j => j.id === option);
                      return job ? `${job.name} (${job.id})` : option;
                    }}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        label="Select Dependencies"
                        placeholder="Choose jobs this job depends on"
                      />
                    )}
                    renderTags={(value, getTagProps) =>
                      value.map((option, index) => {
                        const job = availableJobs.find(j => j.id === option);
                        return (
                          <Chip
                            {...getTagProps({ index })}
                            key={option}
                            label={job ? job.name : option}
                            size="small"
                          />
                        );
                      })
                    }
                    onChange={(_, newValue) => {
                      setValue('dependencies', newValue);
                    }}
                  />
                )}
              />
              <Typography variant="caption" color="textSecondary">
                Select jobs that must complete before this job can start
              </Typography>
            </Box>

            {/* Tags */}
            <Box>
              <Typography variant="h6" gutterBottom>
                Tags
              </Typography>
              <Stack spacing={2}>
                <Autocomplete
                  freeSolo
                  options={commonTags}
                  inputValue={tagInput}
                  onInputChange={(_, newInputValue) => {
                    setTagInput(newInputValue);
                  }}
                  onChange={(_, newValue) => {
                    if (typeof newValue === 'string') {
                      handleAddTag(newValue);
                    }
                  }}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="Add Tags"
                      placeholder="Type or select tags"
                      onKeyPress={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleAddTag(tagInput);
                        }
                      }}
                    />
                  )}
                />
                
                {watchedTags.length > 0 && (
                  <Box>
                    <Typography variant="body2" color="textSecondary" gutterBottom>
                      Selected Tags:
                    </Typography>
                    <Box display="flex" gap={1} flexWrap="wrap">
                      {watchedTags.map((tag) => (
                        <Chip
                          key={tag}
                          label={tag}
                          onDelete={() => handleRemoveTag(tag)}
                          size="small"
                          color="primary"
                          variant="outlined"
                        />
                      ))}
                    </Box>
                  </Box>
                )}
              </Stack>
            </Box>
          </Stack>
        </DialogContent>

        <DialogActions>
          <Button onClick={handleClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={isSubmitting}
            startIcon={<Add />}
          >
            {isSubmitting ? 'Creating...' : 'Create Job'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default JobCreationForm;
