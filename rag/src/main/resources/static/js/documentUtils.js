const documentUtils = {
    getIconClass: function(filename) {
        if (!filename) return 'fa-file';
        const ext = filename.split('.').pop().toLowerCase();
        const iconMap = {
            'pdf': 'fa-file-pdf-o',
            'docx': 'fa-file-word-o',
            'doc': 'fa-file-word-o',
            'xlsx': 'fa-file-excel-o',
            'xls': 'fa-file-excel-o',
            'pptx': 'fa-file-powerpoint-o',
            'ppt': 'fa-file-powerpoint-o',
            'txt': 'fa-file-text-o',
            'png': 'fa-file-image-o',
            'jpg': 'fa-file-image-o',
            'jpeg': 'fa-file-image-o',
            'gif': 'fa-file-image-o',
            'bmp': 'fa-file-image-o'
        };
        return iconMap[ext] || 'fa-file-o';
    },
    
    getIconBgClass: function(filename) {
        if (!filename) return 'bg-blue-100';
        const ext = filename.split('.').pop().toLowerCase();
        const bgMap = {
            'pdf': 'bg-red-100',
            'docx': 'bg-blue-100',
            'doc': 'bg-blue-100',
            'xlsx': 'bg-green-100',
            'xls': 'bg-green-100',
            'pptx': 'bg-orange-100',
            'ppt': 'bg-orange-100',
            'txt': 'bg-gray-100',
            'png': 'bg-purple-100',
            'jpg': 'bg-purple-100',
            'jpeg': 'bg-purple-100',
            'gif': 'bg-purple-100',
            'bmp': 'bg-purple-100'
        };
        return bgMap[ext] || 'bg-gray-100';
    },
    
    getIconColorClass: function(filename) {
        if (!filename) return 'text-blue-600';
        const ext = filename.split('.').pop().toLowerCase();
        const colorMap = {
            'pdf': 'text-red-600',
            'docx': 'text-blue-600',
            'doc': 'text-blue-600',
            'xlsx': 'text-green-600',
            'xls': 'text-green-600',
            'pptx': 'text-orange-600',
            'ppt': 'text-orange-600',
            'txt': 'text-gray-600',
            'png': 'text-purple-600',
            'jpg': 'text-purple-600',
            'jpeg': 'text-purple-600',
            'gif': 'text-purple-600',
            'bmp': 'text-purple-600'
        };
        return colorMap[ext] || 'text-gray-600';
    }
};